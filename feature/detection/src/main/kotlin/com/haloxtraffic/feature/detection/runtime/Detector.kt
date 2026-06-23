package com.haloxtraffic.feature.detection.runtime

import com.haloxtraffic.core.model.BoundingBox
import com.haloxtraffic.core.model.DetectionConfig
import com.haloxtraffic.core.model.InferenceDelegate
import com.haloxtraffic.feature.detection.model.ModelSpec
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Result of one detector inference. */
data class DetectionResult(
    val boxes: List<BoundingBox>,
    val activeDelegate: InferenceDelegate,
    val inferenceMs: Long,
)

/** On-device object detector contract (Stage 1). */
interface Detector {
    val activeDelegate: InferenceDelegate?
    fun isReady(): Boolean

    /** Load the model with delegate fallback. Returns the delegate that initialised. */
    fun init(modelFile: File, spec: ModelSpec, config: DetectionConfig): InferenceDelegate

    /**
     * Run inference on a preprocessed input (NCHW/NHWC per export). Returns boxes via [Yolo26Decoder].
     * @param input preprocessed pixel data matching the model input.
     */
    fun detect(input: FloatArray): DetectionResult

    fun close()
}

/**
 * LiteRT-backed YOLO26 detector (§4). Phase-1 skeleton: the delegate-fallback flow, contract and
 * decoder are wired; the LiteRT interpreter calls land in Phase 2 once the dependency + exported model
 * are confirmed. [detect] throws until initialised — it NEVER emits fabricated detections.
 */
@Singleton
class LiteRtDetector @Inject constructor() : Detector {

    private var spec: ModelSpec? = null
    private var config: DetectionConfig? = null
    override var activeDelegate: InferenceDelegate? = null
        private set

    override fun isReady(): Boolean = false // becomes true when the interpreter is loaded (Phase 2)

    override fun init(modelFile: File, spec: ModelSpec, config: DetectionConfig): InferenceDelegate {
        require(modelFile.exists()) { "Model not provisioned: ${modelFile.name}" }
        this.spec = spec
        this.config = config
        // Phase 2: walk DelegateSelector.chain(config); create a LiteRT interpreter per delegate,
        // use the first that initialises; log it. Until then, record the intended top delegate.
        val intended = DelegateSelector.chain(config).first()
        activeDelegate = intended
        Timber.i("LiteRtDetector configured for ${spec.variant} (delegate=$intended) — interpreter load pending Phase 2")
        return intended
    }

    override fun detect(input: FloatArray): DetectionResult {
        val layout = spec?.detectorOutput
            ?: error("Detector not initialised")
        // Phase 2: run interpreter → raw output → Yolo26Decoder.decode(output, layout).
        // Never fabricate detections; fail loudly until the real path exists.
        throw NotImplementedError(
            "LiteRT inference lands in Phase 2 (decoder ready for ${layout.numDetections}-box NMS-free output)",
        )
    }

    override fun close() {
        activeDelegate = null
        spec = null
        config = null
    }
}
