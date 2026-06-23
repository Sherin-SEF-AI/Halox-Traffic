package com.haloxtraffic.feature.detection.runtime

import android.app.ActivityManager
import android.content.Context
import com.haloxtraffic.core.sensors.profile.HardwareDelegateProbe
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Conservative GPU-delegate capability probe used during device profiling. Phase-1 implementation
 * keys off the GLES version (≥ 3.1 ⇒ GPU delegate viable) so `:core:sensors` stays free of ML deps.
 *
 * TODO(Phase 2): replace the heuristic with LiteRT's authoritative
 * `CompatibilityList().isDelegateSupportedOnThisDevice` once the LiteRT dependency is wired.
 */
@Singleton
class LiteRtDelegateProbe @Inject constructor(
    @ApplicationContext private val context: Context,
) : HardwareDelegateProbe {

    override fun isGpuDelegateSupported(): Boolean = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val glEs = am.deviceConfigurationInfo.reqGlEsVersion // e.g. 0x00030001 == GLES 3.1
        val supported = glEs >= GLES_31
        Timber.d("GPU delegate heuristic: glEs=0x%08x supported=%b", glEs, supported)
        supported
    }.getOrDefault(false)

    private companion object {
        const val GLES_31 = 0x00030001
    }
}
