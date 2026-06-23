package com.haloxtraffic.feature.anpr

import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** On-device plate-text recognizer contract (Stage 4). */
interface PlateOcrEngine {
    fun isReady(): Boolean
    fun init(modelFile: File)

    /**
     * Recognize the plate text in a preprocessed crop. Returns a single [OcrRead]; the pipeline calls
     * this on the top-N sharpest crops and fuses the results via [PlateConsensus].
     * @param crop preprocessed grayscale/normalized recognizer input.
     */
    fun recognize(crop: FloatArray, width: Int, height: Int): OcrRead

    fun close()
}

/**
 * PaddleOCR PP-OCRv5 recognizer (§7C), Indian-plate fine-tuned, tier-selected variant. Phase-1
 * skeleton: contract + lifecycle wired; the recognizer runtime (Paddle-Lite or ONNX/LiteRT, decided
 * at build time) lands in Phase 4. [recognize] throws until initialised — it NEVER fabricates a read.
 */
@Singleton
class PaddleOcrEngine @Inject constructor() : PlateOcrEngine {

    private var modelFile: File? = null

    override fun isReady(): Boolean = false // true once the recognizer is loaded (Phase 4)

    override fun init(modelFile: File) {
        require(modelFile.exists()) { "OCR model not provisioned: ${modelFile.name}" }
        this.modelFile = modelFile
        Timber.i("PaddleOcrEngine configured (${modelFile.name}) — recognizer load pending Phase 4")
    }

    override fun recognize(crop: FloatArray, width: Int, height: Int): OcrRead {
        throw NotImplementedError("PP-OCRv5 recognition lands in Phase 4 (consensus + validation already wired)")
    }

    override fun close() {
        modelFile = null
    }
}
