package com.haloxtraffic.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.haloxtraffic.core.data.repository.RetentionManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Periodic storage retention (§14). Runs only when connected (so purges happen against synced cases)
 * and delegates the sync + integrity gating to [RetentionManager]. Never deletes unsynced/unverified
 * evidence; a no-op when retention is disabled (days == 0).
 */
@HiltWorker
class RetentionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val retentionManager: RetentionManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching { retentionManager.purge() }
            .map { Result.success() }
            .getOrElse { Timber.e(it, "Retention purge failed"); Result.retry() }
    }

    companion object {
        private const val UNIQUE = "halox-retention"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
