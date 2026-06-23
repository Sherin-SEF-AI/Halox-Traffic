package com.haloxtraffic.core.sensors.profile

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.haloxtraffic.core.model.DeviceProfile
import com.haloxtraffic.core.model.DeviceTier
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Probes whether a hardware-accelerated inference delegate is usable on this device. Implemented in a
 * module that owns the LiteRT dependency (`:feature:detection` / `:app`) so `:core:sensors` stays free
 * of ML deps and unit-testable. Defaults to the conservative [None] when no probe is bound.
 */
interface HardwareDelegateProbe {
    fun isGpuDelegateSupported(): Boolean

    object None : HardwareDelegateProbe {
        override fun isGpuDelegateSupported() = false
    }
}

/**
 * Profiles the device at first launch / config change and assigns a [DeviceTier] (§2). The tier drives
 * every heavy component's variant selection. The decision itself is the pure [assignTier] so it is
 * fully unit-testable without an Android runtime.
 */
@Singleton
class DeviceProfiler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val delegateProbe: HardwareDelegateProbe,
) {
    fun profile(): DeviceProfile {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalRamMb = mem.totalMem / (1024 * 1024)

        val abis = Build.SUPPORTED_ABIS?.toList() ?: emptyList()
        val nnapi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 // NNAPI: API 27+
        val gpu = runCatching { delegateProbe.isGpuDelegateSupported() }.getOrElse {
            Timber.w(it, "GPU delegate probe failed; assuming unsupported")
            false
        }
        val thermal = readThermalHeadroom()
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim()
        } else {
            "${Build.MANUFACTURER} ${Build.HARDWARE}".trim()
        }

        val tier = assignTier(totalRamMb, gpu, nnapi, abis)
        return DeviceProfile(
            totalRamMb = totalRamMb,
            abis = abis,
            socModel = soc.ifBlank { "unknown" },
            nnapiAvailable = nnapi,
            gpuDelegateAvailable = gpu,
            thermalHeadroom = thermal,
            tier = tier,
        ).also { Timber.i("Device profiled: ${it.summary}") }
    }

    private fun readThermalHeadroom(): Float {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0f
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        // Map discrete thermal status to a 0f (nominal) .. 1f (critical) headroom proxy.
        return when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> 0f
            PowerManager.THERMAL_STATUS_LIGHT -> 0.2f
            PowerManager.THERMAL_STATUS_MODERATE -> 0.4f
            PowerManager.THERMAL_STATUS_SEVERE -> 0.6f
            PowerManager.THERMAL_STATUS_CRITICAL -> 0.8f
            else -> 1f
        }
    }

    companion object {
        const val MID_RAM_FLOOR_MB = 6_000L
        const val HIGH_RAM_FLOOR_MB = 8_000L

        /**
         * Pure tier decision (§2). HIGH needs ample RAM + a usable GPU delegate; MID needs mid RAM and
         * any accelerator; everything else is LOW (which must still run the core loop). A device with no
         * 64-bit ABI is held to LOW regardless of RAM.
         */
        fun assignTier(
            totalRamMb: Long,
            gpuDelegate: Boolean,
            nnapi: Boolean,
            abis: List<String>,
        ): DeviceTier {
            val has64Bit = abis.any { it.contains("arm64") || it.contains("x86_64") }
            if (!has64Bit) return DeviceTier.LOW
            return when {
                totalRamMb >= HIGH_RAM_FLOOR_MB && gpuDelegate -> DeviceTier.HIGH
                totalRamMb >= MID_RAM_FLOOR_MB && (gpuDelegate || nnapi) -> DeviceTier.MID
                else -> DeviceTier.LOW
            }
        }
    }
}
