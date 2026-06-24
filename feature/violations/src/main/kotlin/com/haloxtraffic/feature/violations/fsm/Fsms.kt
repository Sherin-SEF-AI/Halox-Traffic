package com.haloxtraffic.feature.violations.fsm

import com.haloxtraffic.core.model.ViolationType
import com.haloxtraffic.feature.violations.Angles
import com.haloxtraffic.feature.violations.TrackObservation
import com.haloxtraffic.feature.violations.ViolationFsm
import com.haloxtraffic.feature.violations.ViolationThresholds

/**
 * The Phase-3 high-confidence FSMs (§6). Each reads only the fields it needs from [TrackObservation];
 * the base class enforces the K-frame confirmation + debounce.
 */

/** No-helmet: a two-wheeler in motion with at least one bare head (rider or pillion). */
class NoHelmetFsm(t: ViolationThresholds) :
    ViolationFsm(ViolationType.NO_HELMET, t.confirmFrames, t.rejectGapFrames) {
    override fun evaluate(observation: TrackObservation): Boolean =
        observation.moving && observation.unhelmetedHeads >= 1
}

/** Triple riding / overloading: three or more persons associated with one two-wheeler. */
class TripleRidingFsm(private val t: ViolationThresholds) :
    ViolationFsm(ViolationType.TRIPLE_RIDING, t.confirmFrames, t.rejectGapFrames) {
    override fun evaluate(observation: TrackObservation): Boolean =
        observation.associatedPersons >= t.tripleRidingMinPersons
}

/** Wrong-way: sustained travel opposing the lane's expected direction (config or inferred flow). */
class WrongWayFsm(private val t: ViolationThresholds) :
    ViolationFsm(ViolationType.WRONG_WAY, t.confirmFrames, t.rejectGapFrames) {
    override fun evaluate(observation: TrackObservation): Boolean {
        val expected = observation.expectedDirectionDeg ?: return false
        val heading = observation.headingDeg ?: return false
        return Angles.diff(heading, expected) >= t.wrongWayAngleDeg
    }
}

/**
 * No / obscured / non-conformant plate. Fires only on positive evidence, never on our own failure to
 * detect or read a plate: a prominent (close, clearly seen) moving vehicle with no plate at all, or a
 * plate ANPR positively read that fails Indian-format validation. A plate we merely could not OCR is
 * NOT a violation. For evidence-grade enforcement precision must beat recall, so we accept missing some
 * genuine cases rather than accusing a law-abiding driver.
 */
class NoOrObscuredPlateFsm(t: ViolationThresholds) :
    ViolationFsm(ViolationType.PLATE_MISSING_OR_OBSCURED, t.confirmFrames, t.rejectGapFrames) {
    override fun evaluate(observation: TrackObservation): Boolean {
        if (!observation.moving) return false
        // Only judge a vehicle we have a genuinely close, clear look at. A missed plate detection on a
        // distant or sharply angled vehicle is our limitation, not the driver's violation.
        if (!observation.vehicleProminent) return false
        val missing = !observation.platePresent
        val nonConformant = observation.plateConformant == false
        return missing || nonConformant
    }
}
