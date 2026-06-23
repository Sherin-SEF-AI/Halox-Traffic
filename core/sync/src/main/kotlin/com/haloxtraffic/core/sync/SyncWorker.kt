package com.haloxtraffic.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Drains the [SyncQueue] when connectivity is available (§13). Phase-1 skeleton: the worker is wired,
 * scheduled and idempotent, but the per-item upload calls land in Phase 9. It must never touch the
 * live capture path. Failures back off exponentially and are surfaced via the queue's error fields.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncQueue: SyncQueue,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val pending = runCatching { syncQueue.pending() }.getOrElse {
            Timber.e(it, "Failed to read sync queue")
            return Result.retry()
        }
        if (pending.isEmpty()) return Result.success()

        Timber.i("SyncWorker: ${pending.size} item(s) pending (upload wired in Phase 9)")
        // Phase 9: for each item → call HaloxApi by entityType, then markSynced / markRetry.
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "halox-sync"

        /** Schedule periodic, connectivity-gated sync. Idempotent (KEEP existing). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
