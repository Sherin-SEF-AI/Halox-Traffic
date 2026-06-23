package com.haloxtraffic.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.haloxtraffic.core.data.entity.SessionEntity
import com.haloxtraffic.core.data.repository.CaseDraft
import com.haloxtraffic.core.data.repository.SealingRepository
import com.haloxtraffic.core.evidence.DefaultEvidenceSealer
import com.haloxtraffic.core.evidence.HashChain
import com.haloxtraffic.core.evidence.Hasher
import com.haloxtraffic.core.evidence.Signer
import com.haloxtraffic.core.model.DeviceTier
import com.haloxtraffic.core.model.MountMode
import com.haloxtraffic.core.model.PlateColor
import com.haloxtraffic.core.model.PlateFormat
import com.haloxtraffic.core.model.PlateRead
import com.haloxtraffic.core.model.TimeTrust
import com.haloxtraffic.core.model.ViolationType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SealingRepositoryTest {

    /** Deterministic signer so the test runs without the Android Keystore. */
    private class FakeSigner : Signer {
        override fun sign(packageHash: String) = "sig:$packageHash"
        override fun verify(packageHash: String, signatureB64: String) = signatureB64 == "sig:$packageHash"
        override fun publicKeyB64() = "fake-pub"
    }

    private lateinit var db: HaloxDatabase
    private lateinit var repo: SealingRepository

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HaloxDatabase::class.java)
            .allowMainThreadQueries().build()
        val hasher = Hasher()
        val chain = HashChain(hasher)
        val signer = FakeSigner()
        repo = SealingRepository(
            caseDao = db.violationCaseDao(),
            evidenceDao = db.evidencePackageDao(),
            plateAuditDao = db.plateAuditDao(),
            sealer = DefaultEvidenceSealer(hasher, chain, signer),
            hashChain = chain,
            signer = signer,
            io = UnconfinedTestDispatcher(),
        )
        db.openHelper.writableDatabase // touch
    }

    @After fun tearDown() = db.close()

    private suspend fun seedSession() = db.sessionDao().upsert(
        SessionEntity("s1", null, "off", MountMode.HANDHELD, 1000, null, DeviceTier.MID, "x", "{}"),
    )

    private fun draft(id: String, ts: Long) = CaseDraft(
        caseId = id, sessionId = "s1", vehicleTrackId = 1, type = ViolationType.NO_HELMET, severity = 1,
        tsMs = ts, lat = 12.0, lon = 77.0, accuracyM = 5f, heading = null, fsmTraceJson = "[]",
        plate = PlateRead("KA05MH2453", 0.9f, emptyList(), PlateFormat.STANDARD, PlateColor.WHITE, true, false, 3),
        vlmDescription = null, timeTrust = TimeTrust.TRUSTED, clip = null, stills = emptyList(),
        plateCrops = emptyList(), officerId = "off", jurisdictionId = null, modelVersionsJson = "{}",
    )

    @Test fun `sealing two cases builds a verifiable chain`() = runTest {
        seedSession()
        repo.sealCommit(draft("c1", 2000))
        repo.sealCommit(draft("c2", 3000))

        assertThat(repo.verifyCase("c1")).isTrue()
        assertThat(repo.verifyCase("c2")).isTrue()
        val integrity = repo.verifyIntegrity()
        assertThat(integrity.ok).isTrue()

        // Second package chains to the first.
        val p2 = db.evidencePackageDao().forCase("c2")!!
        assertThat(p2.prevHash).isNotNull()
    }

    @Test fun `tampering a sealed package is detected`() = runTest {
        seedSession()
        repo.sealCommit(draft("c1", 2000))
        repo.sealCommit(draft("c2", 3000))

        // Mutate a sealed package directly (simulating tampering of the immutable store).
        db.openHelper.writableDatabase.execSQL("UPDATE evidence_package SET sha256 = 'deadbeef' WHERE caseId = 'c1'")

        assertThat(repo.verifyCase("c1")).isFalse()
        assertThat(repo.verifyIntegrity().ok).isFalse()
    }

    @Test fun `plate correction appends an audit row and updates the displayed plate`() = runTest {
        seedSession()
        repo.sealCommit(draft("c1", 2000))
        assertThat(db.plateAuditDao().observeForCase("c1").first()).hasSize(1) // initial read

        repo.correctPlate("c1", "ka05mh2453", reviewerId = "rev1", reason = "typo")
        val audits = db.plateAuditDao().observeForCase("c1").first()
        assertThat(audits).hasSize(2)
        assertThat(audits.last().correctedRead).isEqualTo("ka05mh2453")
        assertThat(db.violationCaseDao().byId("c1")!!.plateString).isEqualTo("ka05mh2453")
    }
}
