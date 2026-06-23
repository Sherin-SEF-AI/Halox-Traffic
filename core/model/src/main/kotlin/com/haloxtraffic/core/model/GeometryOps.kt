package com.haloxtraffic.core.model

/**
 * Pure geometry tests for violation FSMs (§6), all in normalised [0,1] image coordinates. Unit-testable
 * without any Android dependency.
 */
object GeometryOps {

    /** Ray-casting point-in-polygon test. */
    fun pointInPolygon(x: Float, y: Float, polygon: Polygon): Boolean {
        val pts = polygon.points
        var inside = false
        var j = pts.size - 1
        for (i in pts.indices) {
            val pi = pts[i]
            val pj = pts[j]
            if (((pi.y > y) != (pj.y > y)) &&
                (x < (pj.x - pi.x) * (y - pi.y) / ((pj.y - pi.y).takeIf { it != 0f } ?: 1e-6f) + pi.x)
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /** True if the box's centre point lies within [polygon] — the stop-line band crossing test. */
    fun boxCenterInPolygon(box: BoundingBox, polygon: Polygon): Boolean =
        pointInPolygon(box.centerX, box.centerY, polygon)

    /**
     * True if the box straddles [boundary]: its left and right edges fall on opposite sides of the
     * boundary line evaluated at the box's vertical centre (lane-straddle test). Uses the polyline
     * segment spanning the box's y.
     */
    fun boxStraddlesBoundary(box: BoundingBox, boundary: LaneBoundary): Boolean {
        val y = box.centerY
        val seg = segmentAtY(boundary.polyline, y) ?: return false
        val (a, b) = seg
        // x of the boundary at height y (linear interpolation along the segment).
        val dy = (b.y - a.y)
        val tt = if (dy == 0f) 0f else ((y - a.y) / dy).coerceIn(0f, 1f)
        val boundaryX = a.x + (b.x - a.x) * tt
        return box.left < boundaryX && box.right > boundaryX
    }

    /** Pick the polyline segment whose y-range contains [y], or the nearest. */
    private fun segmentAtY(polyline: List<NormPoint>, y: Float): Pair<NormPoint, NormPoint>? {
        if (polyline.size < 2) return null
        for (i in 0 until polyline.size - 1) {
            val a = polyline[i]
            val b = polyline[i + 1]
            val lo = minOf(a.y, b.y)
            val hi = maxOf(a.y, b.y)
            if (y in lo..hi) return a to b
        }
        // Fall back to the closest endpoint segment.
        return polyline[0] to polyline[1]
    }
}
