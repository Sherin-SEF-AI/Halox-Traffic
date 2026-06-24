package com.haloxtraffic.feature.violations

/**
 * Tunable thresholds for the violation FSMs (§6), surfaced in Settings later. Defaults are conservative
 * to favour low false-positive rate over recall.
 *
 * @param confirmFrames K — frames a criterion must hold before COMMIT.
 * @param rejectGapFrames frames of non-criteria that reset confirmation.
 * @param movingSpeed normalised units/frame above which a track counts as moving.
 * @param wrongWayAngleDeg heading vs expected-flow angular difference that counts as wrong-way.
 * @param tripleRidingMinPersons persons on one two-wheeler that trip overloading.
 * @param minFlowTracksForInference moving tracks needed to infer majority flow direction.
 */
data class ViolationThresholds(
    val confirmFrames: Int = 5,
    val rejectGapFrames: Int = 3,
    // A track must move at least this fraction of the frame per frame to count as moving. Set above
    // handheld camera jitter so a still scene (or a photo of traffic) does not read as moving traffic.
    val movingSpeed: Float = 0.012f,
    val wrongWayAngleDeg: Float = 120f,
    val tripleRidingMinPersons: Int = 3,
    val minFlowTracksForInference: Int = 3,
)

/** Angle helpers for direction-of-travel comparison (degrees). */
object Angles {
    /** Smallest absolute difference between two headings, in [0,180]. */
    fun diff(a: Float, b: Float): Float {
        var d = kotlin.math.abs(a - b) % 360f
        if (d > 180f) d = 360f - d
        return d
    }

    /** Heading in degrees [0,360) of a velocity vector, or null if below [minSpeed]. */
    fun heading(vx: Float, vy: Float, minSpeed: Float): Float? {
        val speed = kotlin.math.hypot(vx, vy)
        if (speed < minSpeed) return null
        val deg = Math.toDegrees(kotlin.math.atan2(vy.toDouble(), vx.toDouble())).toFloat()
        return (deg + 360f) % 360f
    }

    /** Circular mean of headings (degrees), or null if empty. */
    fun circularMean(headings: List<Float>): Float? {
        if (headings.isEmpty()) return null
        var sx = 0.0
        var sy = 0.0
        for (h in headings) {
            sx += kotlin.math.cos(Math.toRadians(h.toDouble()))
            sy += kotlin.math.sin(Math.toRadians(h.toDouble()))
        }
        val deg = Math.toDegrees(kotlin.math.atan2(sy, sx)).toFloat()
        return (deg + 360f) % 360f
    }
}
