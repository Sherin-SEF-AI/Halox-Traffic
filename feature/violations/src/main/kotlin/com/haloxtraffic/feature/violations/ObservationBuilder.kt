package com.haloxtraffic.feature.violations

import com.haloxtraffic.core.model.BoundingBox
import com.haloxtraffic.core.model.DetectionClass
import com.haloxtraffic.core.model.GeometryOps
import com.haloxtraffic.core.model.JunctionGeometry
import com.haloxtraffic.core.model.NormPoint
import com.haloxtraffic.core.model.Track
import com.haloxtraffic.core.model.VehicleClass
import com.haloxtraffic.feature.violations.tracking.MultiObjectTracker

/**
 * Turns a frame's raw detections (§4 output) into per-vehicle [TrackObservation]s (§6): tracks the
 * vehicles, associates persons / helmet-heads / plates / phones / seatbelts by spatial containment,
 * infers the expected travel direction, and applies junction [geometry] for the viewpoint-dependent
 * cues (signal state, stop-line crossing, lane straddle). Stateful via the embedded tracker.
 */
class ObservationBuilder(
    private val tracker: MultiObjectTracker,
    private val thresholds: ViolationThresholds,
    var geometry: JunctionGeometry = JunctionGeometry.EMPTY,
) {
    fun build(boxes: List<BoundingBox>, frame: Long): List<TrackObservation> {
        val byClass = boxes.groupBy { DetectionClass.fromId(it.classId) }
        val vehicles = boxes.filter { DetectionClass.fromId(it.classId)?.isVehicle == true }
        val persons = byClass[DetectionClass.PERSON].orEmpty()
        val helmets = byClass[DetectionClass.HELMET].orEmpty()
        val bareHeads = byClass[DetectionClass.NO_HELMET].orEmpty()
        val plates = byClass[DetectionClass.PLATE].orEmpty()
        val phones = byClass[DetectionClass.PHONE].orEmpty()
        val seatbelts = byClass[DetectionClass.SEATBELT].orEmpty()
        val redLights = byClass[DetectionClass.TRAFFIC_LIGHT_RED].orEmpty()

        val tracks = tracker.update(vehicles, frame)

        // Signal state: a red light is present (optionally constrained to the configured ROI).
        val signalRed = redLights.any { red ->
            geometry.signalRoi?.let { GeometryOps.boxCenterInPolygon(red, it) } ?: true
        }

        // Expected direction: explicit lane config wins, else inferred majority flow.
        val headings = tracks.mapNotNull { Angles.heading(it.velocity.x, it.velocity.y, thresholds.movingSpeed) }
        val inferred = if (headings.size >= thresholds.minFlowTracksForInference) Angles.circularMean(headings) else null
        val expectedDirection = geometry.defaultLaneDirectionDeg ?: inferred

        return tracks.map { track ->
            val heading = Angles.heading(track.velocity.x, track.velocity.y, thresholds.movingSpeed)
            val headBox = track.box.expandedTop(HEAD_EXPAND)
            val windshield = track.box.upperRegion(WINDSHIELD_FRAC)
            val plate = plates.filter { it.center in track.box }.maxByOrNull { it.score }
            val isFourWheeler = track.vehicleClass == VehicleClass.CAR || track.vehicleClass == VehicleClass.TRUCK

            val personsInWindshield = persons.count { it.center in windshield }
            val seatbeltsInWindshield = seatbelts.count { it.center in windshield }

            TrackObservation(
                frame = frame,
                track = track,
                moving = heading != null,
                associatedPersons = persons.count { it.center in track.box },
                helmetedHeads = helmets.count { it.center in headBox },
                unhelmetedHeads = bareHeads.count { it.center in headBox },
                platePresent = plate != null,
                plateReadable = plate != null && plate.score >= PLATE_READABLE_SCORE &&
                    plate.area >= PLATE_MIN_AREA,
                plateConformant = null, // resolved by ANPR in Phase 4
                expectedDirectionDeg = expectedDirection,
                headingDeg = heading,
                signalRed = signalRed,
                crossedStopLine = geometry.stopLine?.let { GeometryOps.boxCenterInPolygon(track.box, it) } ?: false,
                seatbeltAbsent = isFourWheeler && personsInWindshield >= 1 && seatbeltsInWindshield == 0,
                phoneNearDriver = phones.any { it.center in windshield },
                laneStraddle = geometry.laneBoundaries.any { GeometryOps.boxStraddlesBoundary(track.box, it) },
            )
        }
    }

    private val BoundingBox.center get() = NormPoint(centerX, centerY)

    private operator fun BoundingBox.contains(p: NormPoint): Boolean =
        p.x in left..right && p.y in top..bottom

    /** Expand a box upward to catch heads sitting above a two-wheeler's body box. */
    private fun BoundingBox.expandedTop(frac: Float): BoundingBox =
        copy(top = (top - height * frac).coerceAtLeast(0f))

    /** Upper [frac] of the box — the windshield / driver region for a 4-wheeler. */
    private fun BoundingBox.upperRegion(frac: Float): BoundingBox =
        copy(bottom = top + height * frac)

    private companion object {
        const val HEAD_EXPAND = 0.4f
        const val WINDSHIELD_FRAC = 0.55f
        const val PLATE_READABLE_SCORE = 0.45f
        const val PLATE_MIN_AREA = 0.0008f
    }
}
