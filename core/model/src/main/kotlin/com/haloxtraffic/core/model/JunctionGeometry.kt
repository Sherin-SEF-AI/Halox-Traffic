package com.haloxtraffic.core.model

import kotlinx.serialization.Serializable

/**
 * A lane boundary polyline in normalised image coordinates, with the lane's expected travel direction
 * (degrees) where known. Used for lane-straddle detection and per-lane wrong-way (§6).
 */
@Serializable
data class LaneBoundary(
    val polyline: List<NormPoint>,
    val expectedDirectionDeg: Float? = null,
) {
    init {
        require(polyline.size >= 2) { "A lane boundary needs at least 2 points" }
    }
}

/**
 * Per-junction enforcement geometry (§6/§9/§12.3), captured over the fixed camera view in normalised
 * image coordinates. All fields optional so a junction enables only the viewpoint-dependent violations
 * its geometry supports (the "positioning flag"): a stop-line enables red-light, lane boundaries enable
 * lane violations, etc.
 *
 * @param stopLine band polygon a vehicle must not cross on red.
 * @param signalRoi region where the traffic-signal state is read (constrains red detection).
 * @param laneBoundaries lane divider polylines.
 * @param defaultLaneDirectionDeg fallback expected travel direction for the whole view.
 */
@Serializable
data class JunctionGeometry(
    val stopLine: Polygon? = null,
    val signalRoi: Polygon? = null,
    val laneBoundaries: List<LaneBoundary> = emptyList(),
    val defaultLaneDirectionDeg: Float? = null,
) {
    val supportsRedLight: Boolean get() = stopLine != null
    val supportsLane: Boolean get() = laneBoundaries.isNotEmpty()

    companion object {
        val EMPTY = JunctionGeometry()
    }
}
