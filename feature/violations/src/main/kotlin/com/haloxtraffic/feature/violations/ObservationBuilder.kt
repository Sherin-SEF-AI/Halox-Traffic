package com.haloxtraffic.feature.violations

import com.haloxtraffic.core.model.BoundingBox
import com.haloxtraffic.core.model.DetectionClass
import com.haloxtraffic.core.model.Track
import com.haloxtraffic.feature.violations.tracking.MultiObjectTracker

/**
 * Turns a frame's raw detections (§4 output) into per-vehicle [TrackObservation]s (§6): tracks the
 * vehicles, then associates persons / helmet-heads / plates to each track by spatial containment, and
 * infers the expected travel direction from majority flow. Stateful via the embedded tracker.
 */
class ObservationBuilder(
    private val tracker: MultiObjectTracker,
    private val thresholds: ViolationThresholds,
) {
    fun build(boxes: List<BoundingBox>, frame: Long): List<TrackObservation> {
        val byClass = boxes.groupBy { DetectionClass.fromId(it.classId) }
        val vehicles = boxes.filter { DetectionClass.fromId(it.classId)?.isVehicle == true }
        val persons = byClass[DetectionClass.PERSON].orEmpty()
        val helmets = byClass[DetectionClass.HELMET].orEmpty()
        val bareHeads = byClass[DetectionClass.NO_HELMET].orEmpty()
        val plates = byClass[DetectionClass.PLATE].orEmpty()

        val tracks = tracker.update(vehicles, frame)

        // Infer the expected direction from the majority flow of moving tracks.
        val headings = tracks.mapNotNull { Angles.heading(it.velocity.x, it.velocity.y, thresholds.movingSpeed) }
        val expectedDirection =
            if (headings.size >= thresholds.minFlowTracksForInference) Angles.circularMean(headings) else null

        return tracks.map { track ->
            val heading = Angles.heading(track.velocity.x, track.velocity.y, thresholds.movingSpeed)
            val headBox = track.box.expandedTop(HEAD_EXPAND)
            val plate = plates.filter { it.center in track.box }.maxByOrNull { it.score }

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
            )
        }
    }

    private val BoundingBox.center get() = com.haloxtraffic.core.model.NormPoint(centerX, centerY)

    private operator fun BoundingBox.contains(p: com.haloxtraffic.core.model.NormPoint): Boolean =
        p.x in left..right && p.y in top..bottom

    /** Expand a box upward to catch heads sitting above a two-wheeler's body box. */
    private fun BoundingBox.expandedTop(frac: Float): BoundingBox =
        copy(top = (top - height * frac).coerceAtLeast(0f))

    private companion object {
        const val HEAD_EXPAND = 0.4f
        const val PLATE_READABLE_SCORE = 0.45f
        const val PLATE_MIN_AREA = 0.0008f
    }
}
