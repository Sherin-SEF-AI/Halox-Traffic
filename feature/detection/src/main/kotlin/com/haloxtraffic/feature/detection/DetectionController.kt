package com.haloxtraffic.feature.detection

import android.graphics.Bitmap
import com.haloxtraffic.core.model.BoundingBox
import com.haloxtraffic.core.model.DetectionClass
import com.haloxtraffic.core.model.DetectionConfig
import com.haloxtraffic.core.model.DeviceTier
import com.haloxtraffic.core.model.InferenceDelegate
import com.haloxtraffic.core.sensors.camera.FrameAnalyzer
import com.haloxtraffic.core.sensors.profile.AdaptiveRuntimeController
import com.haloxtraffic.core.sensors.profile.BatteryMonitor
import com.haloxtraffic.core.sensors.profile.ThermalMonitor
import com.haloxtraffic.feature.detection.model.ModelKind
import com.haloxtraffic.feature.detection.model.ModelProvisioner
import com.haloxtraffic.feature.detection.model.ModelRegistry
import com.haloxtraffic.feature.detection.model.ProvisionState
import com.haloxtraffic.feature.detection.runtime.Detector
import com.haloxtraffic.feature.detection.runtime.FramePreprocessor
import com.haloxtraffic.feature.detection.runtime.LetterboxTransform
import com.haloxtraffic.feature.detection.runtime.PreprocessedFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureNanoTime

/** Lifecycle/health of the detector pipeline, surfaced in the HUD. */
enum class DetectorStatus { IDLE, PROVISIONING, LOADING, RUNNING, NO_MODEL, ERROR }

/** One processed frame's detections (boxes are upright-frame normalised) + per-stage latency. */
data class DetectionFrame(
    val boxes: List<BoundingBox>,
    val uprightWidth: Int,
    val uprightHeight: Int,
    val preprocessMs: Long,
    val inferenceMs: Long,
    val delegate: InferenceDelegate,
)

/**
 * Drives Stage 1 (§3): provision the detector model, load it with delegate fallback, then process
 * camera frames behind a cadence gate, feeding measured latency + thermal pressure to the
 * [AdaptiveRuntimeController]. Inference runs on the camera analysis thread (already off-main); results
 * are published to [frames]. Never emits fabricated detections — if the model isn't ready the analyzer
 * simply skips frames and the status reflects why.
 */
@Singleton
class DetectionController @Inject constructor(
    private val detector: Detector,
    private val preprocessor: FramePreprocessor,
    private val provisioner: ModelProvisioner,
    private val registry: ModelRegistry,
    private val adaptiveRuntime: AdaptiveRuntimeController,
    private val thermalMonitor: ThermalMonitor,
    private val batteryMonitor: BatteryMonitor,
) {
    private val _status = MutableStateFlow(DetectorStatus.IDLE)
    val status: StateFlow<DetectorStatus> = _status.asStateFlow()

    private val _frames = MutableStateFlow<DetectionFrame?>(null)
    val frames: StateFlow<DetectionFrame?> = _frames.asStateFlow()

    /** Detector class id → label, for overlay/HUD (canonical taxonomy order). */
    val labels: List<String> = registry.detectorClasses

    /** Detection classes the loaded model can emit — used to gate which violations run. */
    val supportedClasses: Set<DetectionClass> get() = detector.supportedClasses

    private var config: DetectionConfig = DetectionConfig.forTier(DeviceTier.LOW)
    private var lastFrameNs = 0L

    /** Recent square model frames retained for plate cropping on COMMIT (§7B best-frame selection). */
    private class RecentFrame(val squareBitmap: Bitmap, val uprightPlateBoxes: List<BoundingBox>, val transform: LetterboxTransform)
    private val recent = ArrayDeque<RecentFrame>()
    private val recentLock = Any()

    /**
     * Load the detector for [tier]. The active detector runs a bundled EfficientDet-Lite0 model, so it
     * works out of the box (no download). Returns true when ready.
     */
    suspend fun start(tier: DeviceTier): Boolean {
        config = DetectionConfig.forTier(tier)
        _status.value = DetectorStatus.LOADING
        return runCatching { detector.init(config) }
            .onSuccess { _status.value = DetectorStatus.RUNNING }
            .onFailure {
                Timber.e(it, "Detector init failed")
                _status.value = DetectorStatus.ERROR
            }
            .isSuccess
    }

    /** Analyzer for [com.haloxtraffic.core.sensors.camera.CameraController.bind]. Always closes the image. */
    fun analyzer(): FrameAnalyzer = FrameAnalyzer { image ->
        try {
            if (!detector.isReady()) return@FrameAnalyzer
            if (!passCadenceGate()) return@FrameAnalyzer

            var preprocessed: PreprocessedFrame? = null
            val preprocessMs = measureNanoTime {
                preprocessed = preprocessor.process(image, config.inputResolutionPx)
            } / 1_000_000
            val pre = preprocessed!!
            val result = detector.detect(pre.bitmap, scoreThreshold = SCORE_THRESHOLD)

            // Treat low-power like thermal pressure so long sessions back off before draining the battery.
            val pressure = maxOf(thermalMonitor.headroom(), if (batteryMonitor.isLowPower()) LOW_POWER_PRESSURE else 0f)
            adaptiveRuntime.report(result.inferenceMs, pressure)

            val uprightBoxes = pre.transform.invert(result.boxes)
            // Retain the model frame + plate boxes for ANPR cropping; the square bitmap is owned by the
            // buffer now (recycled on eviction).
            retainFrame(pre.bitmap, uprightBoxes, pre.transform)

            _frames.value = DetectionFrame(
                boxes = uprightBoxes,
                uprightWidth = pre.uprightWidth,
                uprightHeight = pre.uprightHeight,
                preprocessMs = preprocessMs,
                inferenceMs = result.inferenceMs,
                delegate = result.activeDelegate,
            )
        } catch (t: Throwable) {
            Timber.e(t, "Detection frame failed")
        } finally {
            image.close()
        }
    }

    fun stop() {
        detector.close()
        clearRecent()
        _frames.value = null
        lastFrameNs = 0L
        _status.value = DetectorStatus.IDLE
    }

    private fun retainFrame(bitmap: Bitmap, uprightBoxes: List<BoundingBox>, transform: LetterboxTransform) {
        val plateBoxes = uprightBoxes.filter { DetectionClass.fromId(it.classId) == DetectionClass.PLATE }
        synchronized(recentLock) {
            recent.addLast(RecentFrame(bitmap, plateBoxes, transform))
            while (recent.size > RECENT_FRAMES) recent.removeFirst().squareBitmap.recycle()
        }
    }

    private fun clearRecent() = synchronized(recentLock) {
        recent.forEach { it.squareBitmap.recycle() }
        recent.clear()
    }

    /**
     * Crop plate candidates for a committed violation (§7A/B): over the buffered frames, take plate
     * detections inside [trackBox] (upright-normalised), map them into the square model bitmap and crop.
     * Returns newest-first, up to [maxCandidates]. The caller (ANPR) ranks them by sharpness.
     */
    fun cropPlatesForTrack(trackBox: BoundingBox, maxCandidates: Int = 12): List<Bitmap> {
        val crops = ArrayList<Bitmap>(maxCandidates)
        synchronized(recentLock) {
            for (frame in recent.reversed()) {
                for (plate in frame.uprightPlateBoxes) {
                    if (plate.centerX !in trackBox.left..trackBox.right) continue
                    if (plate.centerY !in trackBox.top..trackBox.bottom) continue
                    cropSquare(frame.squareBitmap, frame.transform.forward(plate))?.let { crops += it }
                    if (crops.size >= maxCandidates) return crops
                }
            }
        }
        return crops
    }

    /**
     * Copies of the buffered model frames (oldest→newest) for the pre/post evidence clip (§8). Copies
     * so the caller can encode them while the live buffer keeps evicting. Caller recycles them.
     */
    fun recentClipFrames(): List<Bitmap> = synchronized(recentLock) {
        recent.map { it.squareBitmap.copy(Bitmap.Config.ARGB_8888, false) }
    }

    /** Crop a square-normalised box (with a small margin) from [src] into a new bitmap. */
    private fun cropSquare(src: Bitmap, squareBox: BoundingBox): Bitmap? {
        val margin = 0.06f
        val l = ((squareBox.left - margin) * src.width).toInt().coerceIn(0, src.width - 1)
        val t = ((squareBox.top - margin) * src.height).toInt().coerceIn(0, src.height - 1)
        val r = ((squareBox.right + margin) * src.width).toInt().coerceIn(l + 1, src.width)
        val b = ((squareBox.bottom + margin) * src.height).toInt().coerceIn(t + 1, src.height)
        val w = r - l
        val h = b - t
        if (w < MIN_CROP_PX || h < MIN_CROP_PX) return null
        return Bitmap.createBitmap(src, l, t, w, h)
    }

    /** Cadence cap: skip frames arriving faster than the current target FPS budget. */
    private fun passCadenceGate(): Boolean {
        val targetFps = adaptiveRuntime.config.value.targetFps.coerceAtLeast(1)
        val minIntervalNs = 1_000_000_000L / targetFps
        val now = System.nanoTime()
        if (now - lastFrameNs < minIntervalNs) return false
        lastFrameNs = now
        return true
    }

    companion object {
        const val SCORE_THRESHOLD = 0.25f
        private const val RECENT_FRAMES = 8
        private const val MIN_CROP_PX = 12
        private const val LOW_POWER_PRESSURE = 0.7f
    }
}
