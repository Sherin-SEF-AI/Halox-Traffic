package com.haloxtraffic.core.sensors.time

import android.os.SystemClock
import com.haloxtraffic.core.model.TimeStamp
import com.haloxtraffic.core.model.TimeTrust
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Produces trustworthy-as-possible timestamps for evidence (§8/§10). When a recent GPS (or NTP) anchor
 * is available the timestamp is [TimeTrust.TRUSTED]; otherwise it falls back to the device wall clock
 * paired with a monotonic reading and is flagged [TimeTrust.UNTRUSTED] — never dropped.
 *
 * GPS time is fed in via [updateGpsAnchor] as fixes arrive; NTP sync (opportunistic, online) can call
 * the same anchor. The monotonic clock (`elapsedRealtimeNanos`) is immune to wall-clock changes and is
 * used to order events within a session.
 */
@Singleton
class TimeSource @Inject constructor() {

    private val lastAnchorWallMs = AtomicLong(0L)
    private val lastAnchorMonotonicNs = AtomicLong(0L)

    /** Record an external time anchor (GPS fix time or NTP), captured against the monotonic clock. */
    fun updateGpsAnchor(gpsEpochMs: Long) {
        lastAnchorWallMs.set(gpsEpochMs)
        lastAnchorMonotonicNs.set(SystemClock.elapsedRealtimeNanos())
    }

    fun now(): TimeStamp {
        val monotonicNs = SystemClock.elapsedRealtimeNanos()
        val anchorWall = lastAnchorWallMs.get()
        val anchorMono = lastAnchorMonotonicNs.get()

        return if (anchorWall > 0 && monotonicNs - anchorMono <= ANCHOR_VALID_FOR_NS) {
            // Project the trusted anchor forward by the monotonic delta — clock-change immune.
            val projectedMs = anchorWall + (monotonicNs - anchorMono) / 1_000_000
            TimeStamp(projectedMs, monotonicNs, TimeZone.getDefault().id, TimeTrust.TRUSTED)
        } else {
            TimeStamp(System.currentTimeMillis(), monotonicNs, TimeZone.getDefault().id, TimeTrust.UNTRUSTED)
        }
    }

    companion object {
        /** An anchor older than this (no recent fix) demotes time to untrusted. */
        const val ANCHOR_VALID_FOR_NS = 5L * 60 * 1_000_000_000 // 5 minutes
    }
}
