package com.haloxtraffic.feature.detection.runtime

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.haloxtraffic.core.model.BoundingBox
import com.haloxtraffic.core.model.DetectionClass
import com.haloxtraffic.core.model.DetectionConfig
import com.haloxtraffic.core.model.InferenceDelegate
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureNanoTime

/**
 * Real on-device detector (Stage 1) backed by MediaPipe Tasks Vision running a bundled EfficientDet-Lite0
 * COCO model. MediaPipe handles preprocessing + NMS + labels internally; we map its COCO labels to the
 * project's [DetectionClass] taxonomy (vehicles + person). Helmet/plate/seatbelt classes aren't in COCO,
 * so those violations are gated off (see [supportedClasses]) rather than firing falsely.
 */
@Singleton
class MediaPipeObjectDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) : Detector {

    private var detector: ObjectDetector? = null

    override var activeDelegate: InferenceDelegate? = null
        private set

    /** EfficientDet-Lite0 (COCO) → the subset of our taxonomy it can emit. */
    override val supportedClasses: Set<DetectionClass> = setOf(
        DetectionClass.PERSON, DetectionClass.CAR, DetectionClass.MOTORCYCLE,
        DetectionClass.BUS, DetectionClass.TRUCK,
    )

    override fun isReady(): Boolean = detector != null

    override fun init(config: DetectionConfig): InferenceDelegate {
        close()
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
            .setRunningMode(RunningMode.IMAGE)
            .setScoreThreshold(MIN_SCORE)
            .setMaxResults(MAX_RESULTS)
            .build()
        detector = ObjectDetector.createFromOptions(context, options)
        activeDelegate = InferenceDelegate.XNNPACK_CPU // MediaPipe default CPU backend for this model
        Timber.i("MediaPipeObjectDetector ready (EfficientDet-Lite0, ${supportedClasses.size} mapped classes)")
        return activeDelegate!!
    }

    override fun detect(input: Bitmap, scoreThreshold: Float): DetectionResult {
        val det = detector ?: error("Detector not initialised")
        val w = input.width.toFloat()
        val h = input.height.toFloat()
        val boxes = ArrayList<BoundingBox>()
        val ns = measureNanoTime {
            val result = det.detect(BitmapImageBuilder(input).build())
            for (d in result.detections()) {
                val cat = d.categories().maxByOrNull { it.score() } ?: continue
                if (cat.score() < scoreThreshold) continue // confident detections only (cuts noisy boxes)
                val cls = labelToClass(cat.categoryName()) ?: continue
                val r = d.boundingBox()
                boxes += BoundingBox(
                    left = (r.left / w).coerceIn(0f, 1f),
                    top = (r.top / h).coerceIn(0f, 1f),
                    right = (r.right / w).coerceIn(0f, 1f),
                    bottom = (r.bottom / h).coerceIn(0f, 1f),
                    score = cat.score(),
                    classId = cls.ordinal,
                )
            }
        }
        if (boxes.isNotEmpty()) Timber.d("Detected ${boxes.size}: ${boxes.joinToString { DetectionClass.entries[it.classId].label }}")
        return DetectionResult(boxes, activeDelegate ?: InferenceDelegate.XNNPACK_CPU, ns / 1_000_000)
    }

    /** Map a COCO label to our taxonomy (null if not represented). */
    private fun labelToClass(label: String?): DetectionClass? = when (label?.lowercase()) {
        "person" -> DetectionClass.PERSON
        "car" -> DetectionClass.CAR
        "motorcycle", "motorbike" -> DetectionClass.MOTORCYCLE
        "bus" -> DetectionClass.BUS
        "truck" -> DetectionClass.TRUCK
        else -> null
    }

    override fun close() {
        detector?.let { runCatching { it.close() } }
        detector = null
        activeDelegate = null
    }

    private companion object {
        const val MODEL_ASSET = "models/efficientdet_lite0.tflite"
        const val MIN_SCORE = 0.3f
        const val MAX_RESULTS = 25
    }
}
