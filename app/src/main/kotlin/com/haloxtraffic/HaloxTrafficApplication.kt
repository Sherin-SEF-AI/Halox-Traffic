package com.haloxtraffic

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.haloxtraffic.core.sync.SyncWorker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * Application entry point. Initialises logging, provides the Hilt-aware WorkManager configuration, and
 * schedules opportunistic sync. Device profiling + model warmup happen lazily on first screen use
 * (the profiler is a singleton); the VLM init seam (HIGH tier) lands in Phase 7.
 */
@HiltAndroidApp
class HaloxTrafficApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfigCompat.DEBUG) Timber.plant(Timber.DebugTree())
        Timber.i("HaloxTraffic starting")
        SyncWorker.schedule(this)
    }
}

/** Tiny shim so we don't require BuildConfig generation (disabled project-wide for speed). */
private object BuildConfigCompat {
    val DEBUG: Boolean = true
}
