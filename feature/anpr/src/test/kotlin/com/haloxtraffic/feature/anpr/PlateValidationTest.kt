package com.haloxtraffic.feature.anpr

import com.google.common.truth.Truth.assertThat
import com.haloxtraffic.core.model.PlateFormat
import org.junit.Test

class PlateValidationTest {

    private val validator = PlateValidator()
    private val corrector = PlateCorrector(validator)
    private val consensus = PlateConsensus()
    private val pipeline = AnprPipeline(consensus, corrector, validator)

    @Test fun `valid standard plate validates`() {
        val v = validator.validate("KA 05 MH 2453")
        assertThat(v.format).isEqualTo(PlateFormat.STANDARD)
        assertThat(v.valid).isTrue()
        assertThat(v.canonical).isEqualTo("KA05MH2453")
    }

    @Test fun `impossible state code is rejected`() {
        val v = validator.validate("ZZ05MH2453")
        assertThat(v.valid).isFalse()
    }

    @Test fun `BH series validates and excludes I and O in suffix`() {
        assertThat(validator.validate("22BH1234A").valid).isTrue()
        assertThat(validator.validate("22BH1234AB").valid).isTrue()
        // 'I' / 'O' are not permitted suffix letters in BH series.
        assertThat(validator.validate("22BH1234IO").format).isEqualTo(PlateFormat.NON_CONFORMANT)
    }

    @Test fun `position-aware correction fixes digit-letter confusions`() {
        // Confusions: O->0, I->1, B->8, S->5 at digit positions; first two stay letters.
        val result = corrector.correct("KAO5MH24S3") // O should become 0, S should become 5
        assertThat(result.validated).isTrue()
        assertThat(result.corrected).isEqualTo("KA05MH2453")
    }

    @Test fun `letter-position correction maps digits back to letters`() {
        // 0 at a series letter position should become O.
        val result = corrector.correct("KA05M0 2453") // 'M0' series → 'MO'
        assertThat(result.corrected).isEqualTo("KA05MO2453")
        assertThat(result.validated).isTrue()
    }

    @Test fun `multi-frame consensus votes per position`() {
        val reads = listOf(
            OcrRead("KA05MH2453", emptyList(), 0.9f),
            OcrRead("KA05MH2453", emptyList(), 0.8f),
            OcrRead("KA05MH2458", emptyList(), 0.4f), // last char outvoted
        )
        val fused = consensus.fuse(reads)!!
        assertThat(fused.text).isEqualTo("KA05MH2453")
        assertThat(fused.framesFused).isEqualTo(3)
    }

    @Test fun `pipeline marks low-confidence read uncertain`() {
        val reads = listOf(OcrRead("KA05MH2453", emptyList(), 0.3f))
        val read = pipeline.resolve(reads, minConfidence = 0.6f)
        assertThat(read.uncertain).isTrue()
        assertThat(read.validated).isFalse()
    }

    @Test fun `empty reads yield explicit unreadable, never a fabricated plate`() {
        val read = pipeline.resolve(emptyList())
        assertThat(read.plate).isNull()
        assertThat(read.uncertain).isTrue()
    }
}
