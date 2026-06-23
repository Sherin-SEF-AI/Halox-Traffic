package com.haloxtraffic.core.data.repository

import com.haloxtraffic.core.data.dao.EvidencePackageDao
import com.haloxtraffic.core.data.dao.SyncQueueDao
import com.haloxtraffic.core.data.dao.ViolationCaseDao
import com.haloxtraffic.core.data.settings.SettingsRepository
import com.haloxtraffic.core.evidence.HashChain
import com.haloxtraffic.core.evidence.Signer
import com.haloxtraffic.core.model.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage retention (§14): purge sealed evidence older than the configured window, but ONLY for cases
 * that have fully synced AND whose package still verifies (chain link + signature). Evidence is never
 * dropped before successful sync + integrity confirmation, and `retentionDays == 0` means never purge.
 * This is the single audited deletion path — distinct from the UI, which can never delete evidence.
 */
@Singleton
class RetentionManager @Inject constructor(
    private val caseDao: ViolationCaseDao,
    private val evidenceDao: EvidencePackageDao,
    private val syncQueueDao: SyncQueueDao,
    private val sealedStore: com.haloxtraffic.core.evidence.SealedStore,
    private val settingsRepository: SettingsRepository,
    private val hashChain: HashChain,
    private val signer: Signer,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** Returns the number of cases purged, honouring the configured retention window. */
    suspend fun purge(nowMs: Long = System.currentTimeMillis()): Int = withContext(io) {
        purgeWith(settingsRepository.settings.first().retentionDays, nowMs)
    }

    /** Core purge with an explicit window — testable without DataStore. [days] <= 0 means never. */
    internal suspend fun purgeWith(days: Int, nowMs: Long): Int = withContext(io) {
        if (days <= 0) return@withContext 0
        val cutoff = nowMs - days * MILLIS_PER_DAY
        var purged = 0
        for (case in caseDao.olderThan(cutoff)) {
            if (syncQueueDao.pendingCountFor(case.id) > 0) continue // not synced → keep
            if (!verifies(case.id)) {
                Timber.w("Retention: skipping ${case.id} — integrity not confirmed")
                continue
            }
            sealedStore.purgeCase(case.id)
            caseDao.deleteCase(case.id) // cascades package + audit
            purged++
        }
        if (purged > 0) Timber.i("Retention purged $purged case(s) older than $days days")
        purged
    }

    private suspend fun verifies(caseId: String): Boolean {
        val pkg = evidenceDao.forCase(caseId) ?: return false
        val link = hashChain.link(pkg.id, pkg.sha256, pkg.prevHash)
        return signer.verify(link.linkHash, pkg.signature)
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}
