package com.haloxtraffic.core.model

import kotlinx.serialization.Serializable

/** A point in normalised [0,1] image coordinates (origin top-left). */
@Serializable
data class NormPoint(val x: Float, val y: Float)

/**
 * Axis-aligned detection box in normalised [0,1] image coordinates. Normalised so boxes survive
 * resolution changes from the adaptive runtime controller.
 */
@Serializable
data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val score: Float,
    val classId: Int,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val area: Float get() = width * height
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    /** Intersection-over-union with [other], used by the tracker and plate-association. */
    fun iou(other: BoundingBox): Float {
        val ix = maxOf(left, other.left)
        val iy = maxOf(top, other.top)
        val ax = minOf(right, other.right)
        val ay = minOf(bottom, other.bottom)
        val iw = (ax - ix).coerceAtLeast(0f)
        val ih = (ay - iy).coerceAtLeast(0f)
        val inter = iw * ih
        val union = area + other.area - inter
        return if (union <= 0f) 0f else inter / union
    }
}

/**
 * A tracked object across frames with a stable [trackId]. [velocity] (normalised units/frame) feeds
 * direction-of-travel estimation for the wrong-way FSM.
 */
@Serializable
data class Track(
    val trackId: Long,
    val vehicleClass: VehicleClass,
    val box: BoundingBox,
    val velocity: NormPoint,
    val firstSeenFrame: Long,
    val lastSeenFrame: Long,
)

/** A closed polygon in normalised image (or map) coordinates — stop-lines, signal ROIs, lane regions. */
@Serializable
data class Polygon(val points: List<NormPoint>) {
    init {
        require(points.size >= 3) { "Polygon needs at least 3 points" }
    }
}
