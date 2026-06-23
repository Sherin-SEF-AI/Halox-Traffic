package com.haloxtraffic.feature.detection

import com.haloxtraffic.core.model.BoundingBox
import com.haloxtraffic.core.model.DetectionConfig
import com.haloxtraffic.core.model.DeviceTier
import com.haloxtraffic.core.model.InferenceDelegate
import com.haloxtraffic.core.sensors.camera.FrameAnalyzer
import com.haloxtraffic.core.sensors.profile.AdaptiveRuntimeController
import com.haloxtraffic.core.sensors.profile.ThermalMonitor
import com.haloxtraffic.feature.detection.model.ModelKind
import com.haloxtraffic.feature.detection.model.ModelProvisioner
import com.haloxtraffic.feature.detection.model.ModelRegistry
import com.haloxtraffic.feature.detection.model.ProvisionState
import com.haloxtraffic.feature.detection.runtime.Detector
import com.haloxtraffic.feature.detection.runtime.FramePreprocessor
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
) {
    private val _status = MutableStateFlow(DetectorStatus.IDLE)
    val status: StateFlow<DetectorStatus> = _status.asStateFlow()

    private val _frames = MutableStateFlow<DetectionFrame?>(null)
    val frames: StateFlow<DetectionFrame?> = _frames.asStateFlow()

    /** Detector class id → label, for overlay/HUD. */
    val labels: List<String> = registry.detectorClasses

    private var config: DetectionConfig = DetectionConfig.forTier(DeviceTier.LOW)
    private var lastFrameNs = 0L

    /**
     * Provision + load the detector for [tier]. Returns true when ready to run. With placeholder model
     * URLs this resolves to [DetectorStatus.NO_MODEL] (download fails) — supply real assets in
     * [ModelRegistry] to enable live inference.
     */
    suspend fun start(tier: DeviceTier): Boolean {
        config = DetectionConfig.forTier(tier)
        val spec = registry.specsFor(config).first { it.kind == ModelKind.DETECTOR }

        _status.value = DetectorStatus.PROVISIONING
        var modelFile: java.io.File? = null
        provisioner.provision(spec).collect { state ->
            when (state) {
                is ProvisionState.Ready -> modelFile = state.file
                is ProvisionState.Cached -> modelFile = state.file
                is ProvisionState.Failed -> {
                    Timber.w("Detector model not available: ${state.reason}")
                    _status.value = DetectorStatus.NO_MODEL
                }
                else -> Unit
            }
        }
        val file = modelFile ?: run {
            if (_status.value != DetectorStatus.NO_MODEL) _status.value = DetectorStatus.NO_MODEL
            return false
        }

        _status.value = DetectorStatus.LOADING
        return runCatching { detector.init(file, spec, config) }
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
            pre.bitmap.recycle()

            adaptiveRuntime.report(result.inferenceMs, thermalMonitor.headroom())

            _frames.value = DetectionFrame(
                boxes = pre.transform.invert(result.boxes),
                uprightWidth = pre.uprightWidth,
                uprightHeight = pre.uprightHeight,
                preprocessMs = preprocessMs,
                inferenceMs = result.inferenceMs,
                delegate = result.delegate,
            )
        } catch (t: Throwable) {
            Timber.e(t, "Detection frame failed")
        } finally {
            image.close()
        }
    }

    fun stop() {
        detector.close()
        _frames.value = null
        lastFrameNs = 0L
        _status.value = DetectorStatus.IDLE
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
    }
}
