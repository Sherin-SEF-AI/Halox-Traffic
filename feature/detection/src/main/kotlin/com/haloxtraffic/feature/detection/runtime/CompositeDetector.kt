package com.haloxtraffic.feature.detection.runtime

import android.content.Context
import android.graphics.Bitmap
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
 * The app's active detector. Stage 1: COCO (EfficientDet) finds vehicles + persons on the full frame.
 * Stage 1b (perf): the heavier YOLO plate + helmet models run ONLY on the cropped vehicle/rider regions
 * (where plates/heads actually are) — far faster and more accurate than full-frame, and only every Nth
 * frame. Crop-local boxes are remapped to full-frame coords. supportedClasses is the union, so the
 * violation gate auto-enables No-Helmet + No/Obscured-Plate. Each extra model is best-effort.
 */
@Singleton
class CompositeDetector @Inject constructor(
    @ApplicationContext context: Context,
    private val coco: MediaPipeObjectDetector,
) : Detector {

    private val plate = OnnxYoloDetector(
        context, "models/plate.onnx", inputSize = 416,
        classMap = mapOf(0 to DetectionClass.PLATE), scoreThreshold = 0.30f,
    )
    private val helmet = OnnxYoloDetector(
        context, "models/helmet.onnx", inputSize = 320,
        classMap = mapOf(0 to DetectionClass.HELMET, 1 to DetectionClass.NO_HELMET),
        scoreThreshold = 0.30f, useNnapi = true, // fixed-shape model → NNAPI-friendly
    )

    override var activeDelegate: InferenceDelegate? = null
        private set

    override val supportedClasses: Set<DetectionClass>
        get() = coco.supportedClasses +
            (if (plate.isReady()) setOf(DetectionClass.PLATE) else emptySet()) +
            (if (helmet.isReady()) setOf(DetectionClass.HELMET, DetectionClass.NO_HELMET) else emptySet())

    override fun isReady(): Boolean = coco.isReady()

    override fun init(config: DetectionConfig): InferenceDelegate {
        activeDelegate = coco.init(config)
        runCatching { plate.init() }.onFailure { Timber.w(it, "Plate detector unavailable") }
        runCatching { helmet.init() }.onFailure { Timber.w(it, "Helmet detector unavailable") }
        return activeDelegate!!
    }

    private var frame = 0L
    private var lastExtra = emptyList<BoundingBox>()

    override fun detect(input: Bitmap, scoreThreshold: Float): DetectionResult {
        val cocoResult = coco.detect(input, scoreThreshold)
        var extraMs = 0L
        if ((plate.isReady() || helmet.isReady()) && frame++ % EVERY == 0L) {
            extraMs = measureNanoTime { lastExtra = runOnCrops(input, cocoResult.boxes) } / 1_000_000
        }
        return cocoResult.copy(
            boxes = cocoResult.boxes + lastExtra,
            inferenceMs = cocoResult.inferenceMs + extraMs,
        )
    }

    /** Run plate (on each vehicle) + helmet (on each motorcycle's rider region), remapped to the frame. */
    private fun runOnCrops(frameBmp: Bitmap, cocoBoxes: List<BoundingBox>): List<BoundingBox> {
        val out = ArrayList<BoundingBox>()
        val vehicles = cocoBoxes.filter { DetectionClass.fromId(it.classId)?.isVehicle == true }
            .sortedByDescending { it.area }
            .take(MAX_VEHICLES)

        for (v in vehicles) {
            if (plate.isReady()) {
                cropRegion(frameBmp, v)?.let { (bmp, region) ->
                    plate.detect(bmp).forEach { out += remap(it, region) }
                    bmp.recycle()
                }
            }
            if (helmet.isReady() && DetectionClass.fromId(v.classId) == DetectionClass.MOTORCYCLE) {
                val rider = v.copy(top = (v.top - v.height * RIDER_UP).coerceAtLeast(0f))
                cropRegion(frameBmp, rider)?.let { (bmp, region) ->
                    helmet.detect(bmp).forEach { out += remap(it, region) }
                    bmp.recycle()
                }
            }
        }
        val motos = vehicles.count { DetectionClass.fromId(it.classId) == DetectionClass.MOTORCYCLE }
        Timber.d("Crops: ${vehicles.size} vehicles ($motos moto) → ${out.size} dets" +
            if (out.isNotEmpty()) " [${out.joinToString { DetectionClass.entries[it.classId].label }}]" else "")
        return out
    }

    /** Crop the frame to a normalised [region]; returns (bitmap, actual region used) or null if too small. */
    private fun cropRegion(frame: Bitmap, region: BoundingBox): Pair<Bitmap, BoundingBox>? {
        val l = (region.left * frame.width).toInt().coerceIn(0, frame.width - 1)
        val t = (region.top * frame.height).toInt().coerceIn(0, frame.height - 1)
        val r = (region.right * frame.width).toInt().coerceIn(l + 1, frame.width)
        val b = (region.bottom * frame.height).toInt().coerceIn(t + 1, frame.height)
        val w = r - l
        val h = b - t
        if (w < MIN_CROP || h < MIN_CROP) return null
        val bmp = Bitmap.createBitmap(frame, l, t, w, h)
        val used = BoundingBox(l.toFloat() / frame.width, t.toFloat() / frame.height, r.toFloat() / frame.width, b.toFloat() / frame.height, 1f, region.classId)
        return bmp to used
    }

    /** Map a crop-local normalised box back to full-frame normalised coords. */
    private fun remap(local: BoundingBox, region: BoundingBox): BoundingBox = local.copy(
        left = region.left + local.left * region.width,
        top = region.top + local.top * region.height,
        right = region.left + local.right * region.width,
        bottom = region.top + local.bottom * region.height,
    )

    override fun close() {
        coco.close(); plate.close(); helmet.close()
        activeDelegate = null
    }

    private companion object {
        const val EVERY = 2L            // run plate/helmet every 2nd frame
        const val MAX_VEHICLES = 5      // cap per-frame crop inferences
        const val MIN_CROP = 24         // skip tiny vehicle boxes
        const val RIDER_UP = 1.0f       // expand a motorcycle box upward to include the rider's head
    }
}
