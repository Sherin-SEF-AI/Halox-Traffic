package com.haloxtraffic.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.haloxtraffic.core.data.entity.EvidencePackageEntity
import com.haloxtraffic.core.data.entity.SessionEntity
import com.haloxtraffic.core.data.entity.ViolationCaseEntity
import com.haloxtraffic.core.model.CaseStatus
import com.haloxtraffic.core.model.DeviceTier
import com.haloxtraffic.core.model.MountMode
import com.haloxtraffic.core.model.TimeTrust
import com.haloxtraffic.core.model.ViolationType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HaloxDatabaseTest {

    private lateinit var db: HaloxDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            HaloxDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    private fun session(id: String) = SessionEntity(
        id = id, jurisdictionId = null, officerId = "off1", mountMode = MountMode.HANDHELD,
        startedAt = 1000, endedAt = null, deviceTier = DeviceTier.MID, deviceMeta = "x",
        modelVersionsJson = "{}",
    )

    private fun case(id: String, sessionId: String, ts: Long, plate: String?) = ViolationCaseEntity(
        id = id, sessionId = sessionId, vehicleTrackId = 1, type = ViolationType.NO_HELMET, severity = 1,
        ts = ts, lat = 12.0, lon = 77.0, accuracyM = 5f, heading = null, fsmTraceJson = "[]",
        plateString = plate, plateConfidence = 0.9f, plateValidated = plate != null, plateColor = null,
        vlmDescription = null, status = CaseStatus.OPEN, evidencePackageId = null,
    )

    @Test fun `insert and query cases by plate`() = runTest {
        db.sessionDao().upsert(session("s1"))
        db.violationCaseDao().insert(case("c1", "s1", 2000, "KA05MH2453"))
        db.violationCaseDao().insert(case("c2", "s1", 3000, "KA05MH2453"))
        db.violationCaseDao().insert(case("c3", "s1", 4000, "TN01AB1234"))

        // observeByPlate is a Flow; collect the first emission.
        val list = db.violationCaseDao().observeByPlate("KA05MH2453").first()
        assertThat(list.map { it.id }).containsExactly("c1", "c2")
    }

    @Test fun `evidence chain returns in seal order`() = runTest {
        db.sessionDao().upsert(session("s1"))
        db.violationCaseDao().insert(case("c1", "s1", 2000, null))
        db.violationCaseDao().insert(case("c2", "s1", 3000, null))

        db.evidencePackageDao().insert(pkg("p1", "c1", prev = null, sealedAt = 10))
        db.evidencePackageDao().insert(pkg("p2", "c2", prev = "h1", sealedAt = 20))

        val chain = db.evidencePackageDao().chainInSealOrder()
        assertThat(chain.map { it.id }).containsExactly("p1", "p2").inOrder()
        assertThat(db.evidencePackageDao().latestHash()).isEqualTo("hash-p2")
    }

    private fun pkg(id: String, caseId: String, prev: String?, sealedAt: Long) = EvidencePackageEntity(
        id = id, caseId = caseId, clipPath = null, stillPathsJson = "[]", plateCropPathsJson = "[]",
        sha256 = "hash-$id", prevHash = prev, signature = "sig", sealedAt = sealedAt,
        timeTrustFlag = TimeTrust.TRUSTED,
    )
}
