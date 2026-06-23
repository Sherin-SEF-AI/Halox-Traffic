package com.haloxtraffic.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.haloxtraffic.core.data.entity.SessionEntity
import com.haloxtraffic.core.data.repository.CaseDraft
import com.haloxtraffic.core.data.repository.RetentionManager
import com.haloxtraffic.core.data.repository.SealingRepository
import com.haloxtraffic.core.data.settings.SettingsRepository
import com.haloxtraffic.core.evidence.DefaultEvidenceSealer
import com.haloxtraffic.core.evidence.HashChain
import com.haloxtraffic.core.evidence.Hasher
import com.haloxtraffic.core.evidence.SealedStore
import com.haloxtraffic.core.evidence.Signer
import com.haloxtraffic.core.model.DeviceTier
import com.haloxtraffic.core.model.MountMode
import com.haloxtraffic.core.model.PlateColor
import com.haloxtraffic.core.model.PlateFormat
import com.haloxtraffic.core.model.PlateRead
import com.haloxtraffic.core.model.TimeTrust
import com.haloxtraffic.core.model.ViolationType
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RetentionManagerTest {

    private class FakeSigner : Signer {
        override fun sign(packageHash: String) = "sig:$packageHash"
        override fun verify(packageHash: String, signatureB64: String) = signatureB64 == "sig:$packageHash"
        override fun publicKeyB64() = "k"
    }

    private val now = 1_000_000_000_000L
    private val day = 86_400_000L
    private lateinit var db: HaloxDatabase
    private lateinit var sealing: SealingRepository
    private lateinit var retention: RetentionManager

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, HaloxDatabase::class.java).allowMainThreadQueries().build()
        val hasher = Hasher(); val chain = HashChain(hasher); val signer = FakeSigner()
        val io = UnconfinedTestDispatcher()
        sealing = SealingRepository(db.violationCaseDao(), db.evidencePackageDao(), db.plateAuditDao(), db.syncQueueDao(), DefaultEvidenceSealer(hasher, chain, signer), chain, signer, io)
        val store = SealedStore(ctx)
        val settings = SettingsRepository(PreferenceDataStoreFactory.create { File(ctx.cacheDir, "t.preferences_pb") })
        retention = RetentionManager(db.violationCaseDao(), db.evidencePackageDao(), db.syncQueueDao(), store, settings, chain, signer, io)
    }

    @After fun tearDown() = db.close()

    private suspend fun seal(id: String, ts: Long) {
        sealing.sealCommit(
            CaseDraft(
                caseId = id, sessionId = "s", vehicleTrackId = 1, type = ViolationType.NO_HELMET, severity = 1,
                tsMs = ts, lat = 12.0, lon = 77.0, accuracyM = 5f, heading = null, fsmTraceJson = "[]",
                plate = PlateRead("KA05MH2453", 0.9f, emptyList(), PlateFormat.STANDARD, PlateColor.WHITE, true, false, 1),
                vlmDescription = null, timeTrust = TimeTrust.TRUSTED, clip = null, stills = emptyList(),
                plateCrops = emptyList(), officerId = "o", jurisdictionId = null, modelVersionsJson = "{}",
            ),
        )
    }

    private suspend fun markCaseSynced(caseId: String) {
        db.syncQueueDao().pending(50).filter { it.entityId == caseId }.forEach { db.syncQueueDao().markSynced(it.id, now) }
    }

    @Test fun `disabled retention purges nothing`() = runTest {
        db.sessionDao().upsert(SessionEntity("s", null, "o", MountMode.HANDHELD, 0, null, DeviceTier.MID, "x", "{}"))
        seal("old", now - 100 * day)
        markCaseSynced("old")
        assertThat(retention.purgeWith(days = 0, nowMs = now)).isEqualTo(0)
        assertThat(db.violationCaseDao().byId("old")).isNotNull()
    }

    @Test fun `purges only old, synced, verifiable cases`() = runTest {
        db.sessionDao().upsert(SessionEntity("s", null, "o", MountMode.HANDHELD, 0, null, DeviceTier.MID, "x", "{}"))
        seal("old_synced", now - 100 * day)
        seal("old_unsynced", now - 100 * day)
        seal("recent", now - 1 * day)
        markCaseSynced("old_synced")
        markCaseSynced("recent")

        val purged = retention.purgeWith(days = 30, nowMs = now)

        assertThat(purged).isEqualTo(1)
        assertThat(db.violationCaseDao().byId("old_synced")).isNull()   // old + synced + verifies → purged
        assertThat(db.violationCaseDao().byId("old_unsynced")).isNotNull() // not synced → kept
        assertThat(db.violationCaseDao().byId("recent")).isNotNull()      // within window → kept
    }
}
