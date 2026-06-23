package com.haloxtraffic.core.model

import kotlinx.serialization.Serializable

/**
 * A location fix attached to a violation. [accuracyM] and [headingDeg] are carried into evidence;
 * a low-accuracy fix is flagged in the HUD/case rather than dropped.
 */
@Serializable
data class GeoFix(
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    /** Compass heading in degrees (0..360), or null if unavailable. */
    val headingDeg: Float?,
    /** Epoch millis the fix was obtained. */
    val obtainedAtMs: Long,
) {
    /** Heuristic for the HUD: a fix worse than this is shown amber. */
    val isWeak: Boolean get() = accuracyM > WEAK_ACCURACY_THRESHOLD_M

    companion object {
        const val WEAK_ACCURACY_THRESHOLD_M = 25f
    }
}

/**
 * A trustworthy-as-possible timestamp for a violation. Pairs a wall-clock instant with a monotonic
 * reading (immune to clock changes) and a [TimeTrust] flag indicating whether it was anchored to an
 * external source.
 */
@Serializable
data class TimeStamp(
    /** Best-available wall-clock epoch millis. */
    val epochMs: Long,
    /** Monotonic nanos (SystemClock.elapsedRealtimeNanos) at capture, for ordering within a session. */
    val monotonicNs: Long,
    val timezoneId: String,
    val trust: TimeTrust,
)
