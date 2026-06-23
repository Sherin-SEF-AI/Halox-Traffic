package com.haloxtraffic.core.sensors.profile

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cheap battery/power-state reads for long-session power management (§14). The adaptive runtime treats
 * a low-power state like thermal pressure — backing off cadence/resolution before draining the battery.
 */
@Singleton
class BatteryMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val powerManager by lazy { context.getSystemService(Context.POWER_SERVICE) as PowerManager }

    /** True in OS power-save mode, or when not charging and below [LOW_PCT]. */
    fun isLowPower(): Boolean {
        if (powerManager.isPowerSaveMode) return true
        val (pct, charging) = batteryState()
        return !charging && pct in 0..LOW_PCT
    }

    private fun batteryState(): Pair<Int, Boolean> {
        val intent: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else 100
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return pct to charging
    }

    private companion object {
        const val LOW_PCT = 15
    }
}
