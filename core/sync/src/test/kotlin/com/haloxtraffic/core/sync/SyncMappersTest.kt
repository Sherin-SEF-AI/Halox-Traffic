package com.haloxtraffic.core.sync

import com.google.common.truth.Truth.assertThat
import com.haloxtraffic.core.data.entity.EvidencePackageEntity
import com.haloxtraffic.core.data.entity.SessionEntity
import com.haloxtraffic.core.data.entity.ViolationCaseEntity
import com.haloxtraffic.core.model.CaseStatus
import com.haloxtraffic.core.model.DeviceTier
import com.haloxtraffic.core.model.MountMode
import com.haloxtraffic.core.model.TimeTrust
import com.haloxtraffic.core.model.ViolationType
import org.junit.Test

class SyncMappersTest {

    @Test fun `session maps to manifest dto`() {
        val s = SessionEntity("s1", "j1", "off", MountMode.DASHBOARD, 1000, 2000, DeviceTier.HIGH, "meta", "{}")
        val dto = s.toDto()
        assertThat(dto.id).isEqualTo("s1")
        assertThat(dto.deviceTier).isEqualTo("HIGH")
        assertThat(dto.endedAtMs).isEqualTo(2000)
    }

    @Test fun `case maps with sealed package integrity fields`() {
        val case = ViolationCaseEntity(
            id = "c1", sessionId = "s1", vehicleTrackId = 1, type = ViolationType.WRONG_WAY, severity = 1,
            ts = 5000, lat = 12.0, lon = 77.0, accuracyM = 4f, heading = null, fsmTraceJson = "[]",
            plateString = "KA05MH2453", plateConfidence = 0.9f, plateValidated = true, plateColor = null,
            vlmDescription = null, status = CaseStatus.OPEN, evidencePackageId = "p1",
        )
        val pkg = EvidencePackageEntity("p1", "c1", null, "[]", "[]", "HASH", "PREV", "SIG", 5000, TimeTrust.TRUSTED)
        val dto = case.toDto(pkg)
        assertThat(dto.type).isEqualTo("WRONG_WAY")
        assertThat(dto.contentHash).isEqualTo("HASH")
        assertThat(dto.prevHash).isEqualTo("PREV")
        assertThat(dto.signature).isEqualTo("SIG")
    }

    @Test fun `case with no package has empty integrity fields`() {
        val case = ViolationCaseEntity(
            id = "c2", sessionId = "s1", vehicleTrackId = 1, type = ViolationType.NO_HELMET, severity = 1,
            ts = 0, lat = 0.0, lon = 0.0, accuracyM = 0f, heading = null, fsmTraceJson = "[]",
            plateString = null, plateConfidence = null, plateValidated = false, plateColor = null,
            vlmDescription = null, status = CaseStatus.OPEN, evidencePackageId = null,
        )
        val dto = case.toDto(null)
        assertThat(dto.contentHash).isEmpty()
        assertThat(dto.prevHash).isNull()
    }
}
