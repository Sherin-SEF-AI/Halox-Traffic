package com.haloxtraffic.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.haloxtraffic.core.data.dao.EvidencePackageDao
import com.haloxtraffic.core.data.dao.SessionDao
import com.haloxtraffic.core.data.dao.ViolationCaseDao
import com.haloxtraffic.core.data.entity.EvidencePackageEntity
import com.haloxtraffic.core.sync.api.CaseBatchRequest
import com.haloxtraffic.core.sync.api.HaloxApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Drains the [SyncQueue] when connectivity allows (§13): builds each pending entity's DTO from the DB
 * and upserts it (idempotent on the client UUID), then uploads the case's sealed media; the server
 * re-verifies hash + signature on ingest. Never touches the live capture path. Failures back off and
 * are surfaced via the queue's error fields. Append-only domain objects make re-sends safe.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncQueue: SyncQueue,
    private val api: HaloxApi,
    private val sessionDao: SessionDao,
    private val caseDao: ViolationCaseDao,
    private val evidenceDao: EvidencePackageDao,
) : CoroutineWorker(appContext, params) {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        val pending = runCatching { syncQueue.pending() }.getOrElse {
            Timber.e(it, "Failed to read sync queue")
            return Result.retry()
        }
        if (pending.isEmpty()) return Result.success()

        // TODO(deployment): obtain + attach an auth token (api.authToken) via an OkHttp interceptor.
        var anyFailure = false
        for (item in pending) {
            val ok = runCatching {
                when (item.entityType) {
                    "session" -> sessionDao.byId(item.entityId)?.let { api.upsertSession(it.toDto()) } != null
                    "case" -> uploadCase(item.entityId)
                    else -> true
                }
            }.getOrElse { e ->
                Timber.w(e, "Sync failed for ${item.entityType}:${item.entityId}")
                syncQueue.markRetry(item.id, e.message ?: "error")
                anyFailure = true
                false
            }
            if (ok) syncQueue.markSynced(item.id, System.currentTimeMillis())
        }
        return if (anyFailure) Result.retry() else Result.success()
    }

    private suspend fun uploadCase(caseId: String): Boolean {
        val case = caseDao.byId(caseId) ?: return true // gone → nothing to sync
        val pkg = evidenceDao.forCase(caseId)
        api.upsertCases(CaseBatchRequest(listOf(case.toDto(pkg))))
        uploadEvidence(caseId, pkg)
        return true
    }

    private suspend fun uploadEvidence(caseId: String, pkg: EvidencePackageEntity?) {
        if (pkg == null) return
        val files = (paths(pkg.stillPathsJson) + paths(pkg.plateCropPathsJson) + listOfNotNull(pkg.clipPath))
            .map(::File).filter { it.exists() }
        if (files.isEmpty()) return
        val parts = files.map { f ->
            MultipartBody.Part.createFormData(
                "media", f.name, f.asRequestBody("application/octet-stream".toMediaTypeOrNull()),
            )
        }
        api.uploadEvidence(caseId, parts)
    }

    private fun paths(jsonArray: String): List<String> =
        runCatching { json.decodeFromString<List<String>>(jsonArray) }.getOrDefault(emptyList())

    companion object {
        private const val UNIQUE_PERIODIC = "halox-sync"
        private const val UNIQUE_ONESHOT = "halox-sync-now"

        private fun connectivity() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Schedule periodic, connectivity-gated sync. Idempotent (KEEP existing). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(connectivity())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Kick a one-time drain now (from the Sync screen). */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(connectivity()).build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
