package com.haloxtraffic.core.data

import com.google.common.truth.Truth.assertThat
import com.haloxtraffic.core.data.analytics.Analytics
import com.haloxtraffic.core.data.entity.ViolationCaseEntity
import com.haloxtraffic.core.model.CaseStatus
import com.haloxtraffic.core.model.ViolationType
import org.junit.Test

class AnalyticsTest {

    private fun case(
        id: String,
        type: ViolationType = ViolationType.NO_HELMET,
        plate: String? = "KA05MH2453",
        validated: Boolean = true,
        ts: Long = 0L,
    ) = ViolationCaseEntity(
        id = id, sessionId = "s", vehicleTrackId = 1, type = type, severity = 1, ts = ts,
        lat = 12.0, lon = 77.0, accuracyM = 5f, heading = null, fsmTraceJson = "[]",
        plateString = plate, plateConfidence = 0.9f, plateValidated = validated, plateColor = null,
        vlmDescription = null, status = CaseStatus.OPEN, evidencePackageId = null,
    )

    @Test fun `empty input is EMPTY`() {
        assertThat(Analytics.compute(emptyList())).isEqualTo(com.haloxtraffic.core.data.analytics.CaseAnalytics.EMPTY)
    }

    @Test fun `counts, rates, repeats and hour buckets`() {
        val cases = listOf(
            case("a", ViolationType.NO_HELMET, plate = "KA01AB1111", validated = true, ts = 3_600_000L),   // hour 1
            case("b", ViolationType.NO_HELMET, plate = "KA01AB1111", validated = true, ts = 3_600_000L),   // repeat, hour 1
            case("c", ViolationType.WRONG_WAY, plate = null, validated = false, ts = 7_200_000L),           // uncertain, hour 2
            case("d", ViolationType.WRONG_WAY, plate = "TN09XY9999", validated = false, ts = 7_200_000L),   // unvalidated, hour 2
        )
        val a = Analytics.compute(cases)

        assertThat(a.total).isEqualTo(4)
        assertThat(a.byType[ViolationType.NO_HELMET]).isEqualTo(2)
        assertThat(a.byType[ViolationType.WRONG_WAY]).isEqualTo(2)
        assertThat(a.validatedRate).isWithin(1e-4f).of(0.5f)   // a, b validated
        assertThat(a.uncertainRate).isWithin(1e-4f).of(0.5f)   // c (no plate) + d (unvalidated)
        assertThat(a.repeatPlates).containsExactly("KA01AB1111" to 2)
        assertThat(a.byHour[1]).isEqualTo(2)
        assertThat(a.byHour[2]).isEqualTo(2)
    }
}
