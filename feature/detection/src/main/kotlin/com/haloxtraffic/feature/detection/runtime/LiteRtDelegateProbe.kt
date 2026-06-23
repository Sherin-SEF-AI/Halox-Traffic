package com.haloxtraffic.feature.detection.runtime

import com.haloxtraffic.core.sensors.profile.HardwareDelegateProbe
import org.tensorflow.lite.gpu.CompatibilityList
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authoritative GPU-delegate capability probe used during device profiling, backed by LiteRT's
 * [CompatibilityList]. Lives here (not in `:core:sensors`) so the sensors module stays free of ML deps
 * while the profiler still gets a real signal. Fails soft to `false` if the check throws.
 */
@Singleton
class LiteRtDelegateProbe @Inject constructor() : HardwareDelegateProbe {

    override fun isGpuDelegateSupported(): Boolean = runCatching {
        CompatibilityList().use { it.isDelegateSupportedOnThisDevice }
    }.getOrElse {
        Timber.w(it, "GPU CompatibilityList check failed; treating GPU delegate as unsupported")
        false
    }
}
