package com.haloxtraffic.core.sensors.profile

import android.content.Context
import android.os.Build
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cheap on-demand thermal pressure reading, normalised 0f (nominal) .. 1f (critical). Feeds the
 * [AdaptiveRuntimeController] so sustained inference backs off before the device throttles hard (§14).
 */
@Singleton
class ThermalMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val powerManager by lazy { context.getSystemService(Context.POWER_SERVICE) as PowerManager }

    fun headroom(): Float {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0f
        return when (powerManager.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> 0f
            PowerManager.THERMAL_STATUS_LIGHT -> 0.2f
            PowerManager.THERMAL_STATUS_MODERATE -> 0.4f
            PowerManager.THERMAL_STATUS_SEVERE -> 0.6f
            PowerManager.THERMAL_STATUS_CRITICAL -> 0.8f
            else -> 1f
        }
    }
}
