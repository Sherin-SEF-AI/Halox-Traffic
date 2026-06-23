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
        scoreThreshold = 0.25f, // fp16 model runs correctly on CPU; NNAPI fp16 is unreliable
    )

    override var activeDelegate: InferenceDelegate? = null
        private set

    override val supportedClasses: Set<DetectionClass>
        get() = coco.supportedClasses +
            (if (plate.isReady()) setOf(DetectionClass.PLATE) else emptySet()) +
            (if (helmet.isReady()) setOf(DetectionClass.HELMET, DetectionClass.NO_HELMET) else emptySet())

    // Runtime gates (set from settings): skip a loaded model when its violation is turned off.
    @Volatile private var plateEnabled = true
    @Volatile private var helmetEnabled = true

    override fun setExtraDetectors(plate: Boolean, helmet: Boolean) {
        plateEnabled = plate
        helmetEnabled = helmet
        Timber.i("Extra detectors → plate=$plate helmet=$helmet")
    }

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
        val anyExtra = (plateEnabled && plate.isReady()) || (helmetEnabled && helmet.isReady())
        if (anyExtra && frame++ % EVERY == 0L) {
            extraMs = measureNanoTime { lastExtra = runOnCrops(input, cocoResult.boxes) } / 1_000_000
        }
        return cocoResult.copy(
            boxes = cocoResult.boxes + lastExtra,
            inferenceMs = cocoResult.inferenceMs + extraMs,
        )
    }

    /** Run plate (on each vehicle) + helmet (on each motorcycle rider), remapped to the frame. */
    private fun runOnCrops(frameBmp: Bitmap, cocoBoxes: List<BoundingBox>): List<BoundingBox> {
        val out = ArrayList<BoundingBox>()
        val vehicles = cocoBoxes.filter { DetectionClass.fromId(it.classId)?.isVehicle == true }
            .sortedByDescending { it.area }
            .take(MAX_VEHICLES)

        // Plate: run on every vehicle crop.
        if (plateEnabled && plate.isReady()) {
            for (v in vehicles) cropRegion(frameBmp, v)?.let { (bmp, region) ->
                plate.detect(bmp).forEach { out += remap(it, region) }
                bmp.recycle()
            }
        }

        // Helmet: heads live on the riders, so run on PERSON boxes that sit on a motorcycle (reliable
        // head containers), expanded slightly upward for head margin.
        val motos = vehicles.filter { DetectionClass.fromId(it.classId) == DetectionClass.MOTORCYCLE }
        var riders = 0
        if (helmetEnabled && helmet.isReady() && motos.isNotEmpty()) {
            val persons = cocoBoxes.filter { DetectionClass.fromId(it.classId) == DetectionClass.PERSON }
                .filter { p -> motos.any { overlaps(it, p) } }
                .sortedByDescending { it.area }
                .take(MAX_RIDERS)
            riders = persons.size
            for (p in persons) {
                val head = p.copy(top = (p.top - p.height * RIDER_UP).coerceAtLeast(0f))
                cropRegion(frameBmp, head)?.let { (bmp, region) ->
                    helmet.detect(bmp).forEach { out += remap(it, region) }
                    bmp.recycle()
                }
            }
        }
        Timber.d("Crops: ${vehicles.size} veh / ${motos.size} moto / $riders riders → ${out.size} dets" +
            if (out.isNotEmpty()) " [${out.joinToString { DetectionClass.entries[it.classId].label }}]" else "")
        return out
    }

    /** True if the two normalised boxes intersect at all. */
    private fun overlaps(a: BoundingBox, b: BoundingBox): Boolean =
        a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top

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
        const val MAX_RIDERS = 4        // cap helmet inferences per frame
        const val RIDER_UP = 0.25f      // expand a rider's person box upward for head margin
    }
}
