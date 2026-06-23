package com.haloxtraffic.feature.detection.runtime

import android.graphics.Bitmap
import com.haloxtraffic.core.model.DetectionClass
import com.haloxtraffic.core.model.DetectionConfig
import com.haloxtraffic.core.model.InferenceDelegate
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureNanoTime

/**
 * The app's active detector: fuses the bundled COCO object detector (vehicles + person) with the
 * bundled YOLOv11 license-plate detector, both run on the same square frame. Its [supportedClasses] is
 * the union, so the violation gate auto-enables plate-dependent logic (No/Obscured-Plate) and feeds
 * plate crops into the ANPR path. The plate detector is best-effort — if it fails to load, detection
 * still runs with vehicles + person.
 */
@Singleton
class CompositeDetector @Inject constructor(
    private val coco: MediaPipeObjectDetector,
    private val plate: OnnxPlateDetector,
) : Detector {

    override var activeDelegate: InferenceDelegate? = null
        private set

    override val supportedClasses: Set<DetectionClass>
        get() = coco.supportedClasses + if (plate.isReady()) setOf(DetectionClass.PLATE) else emptySet()

    override fun isReady(): Boolean = coco.isReady()

    override fun init(config: DetectionConfig): InferenceDelegate {
        activeDelegate = coco.init(config)
        runCatching { plate.init(PLATE_INPUT) }
            .onFailure { Timber.w(it, "Plate detector unavailable; continuing with COCO only") }
        return activeDelegate!!
    }

    override fun detect(input: Bitmap, scoreThreshold: Float): DetectionResult {
        val cocoResult = coco.detect(input, scoreThreshold)
        var plateBoxes = emptyList<com.haloxtraffic.core.model.BoundingBox>()
        val ns = measureNanoTime { plateBoxes = plate.detect(input) }
        return cocoResult.copy(
            boxes = cocoResult.boxes + plateBoxes,
            inferenceMs = cocoResult.inferenceMs + ns / 1_000_000,
        )
    }

    override fun close() {
        coco.close()
        plate.close()
        activeDelegate = null
    }

    private companion object {
        const val PLATE_INPUT = 640
    }
}
