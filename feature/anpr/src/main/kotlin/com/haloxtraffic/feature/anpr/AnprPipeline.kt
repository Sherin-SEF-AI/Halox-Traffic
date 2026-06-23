package com.haloxtraffic.feature.anpr

import com.haloxtraffic.core.model.PlateColor
import com.haloxtraffic.core.model.PlateRead
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assembles raw multi-frame OCR reads into a final [PlateRead] (§7): consensus → format-constrained
 * correction → validation. The plate string ALWAYS carries a confidence and a `validated` flag; a
 * low-confidence or unvalidatable read is surfaced as uncertain (for human/VLM confirmation), never as
 * a clean result, and an empty read returns [PlateRead.unreadable]. Pure orchestration — unit-testable
 * by feeding [OcrRead]s directly without a recognizer runtime.
 */
@Singleton
class AnprPipeline @Inject constructor(
    private val consensus: PlateConsensus,
    private val corrector: PlateCorrector,
    private val validator: PlateValidator,
) {
    /**
     * @param reads OCR reads from the top-N sharpest crops of one plate.
     * @param color classified plate background colour (UNKNOWN if not yet classified).
     * @param minConfidence below this overall consensus confidence, the read is flagged uncertain.
     */
    fun resolve(
        reads: List<OcrRead>,
        color: PlateColor = PlateColor.UNKNOWN,
        minConfidence: Float = 0.6f,
    ): PlateRead {
        val fused = consensus.fuse(reads) ?: return PlateRead.unreadable()

        val correction = corrector.correct(fused.text)
        val validation = validator.validate(correction.corrected)

        val lowConfidence = fused.overall < minConfidence
        val uncertain = lowConfidence || !validation.valid

        return PlateRead(
            plate = validation.canonical.ifEmpty { null },
            confidence = fused.overall,
            perCharConfidence = fused.perCharConfidence,
            format = validation.format,
            color = color,
            validated = validation.valid && !lowConfidence,
            uncertain = uncertain,
            framesFused = fused.framesFused,
        )
    }
}
