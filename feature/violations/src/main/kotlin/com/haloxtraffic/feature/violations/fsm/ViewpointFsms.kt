package com.haloxtraffic.feature.violations.fsm

import com.haloxtraffic.core.model.ViolationType
import com.haloxtraffic.feature.violations.TrackObservation
import com.haloxtraffic.feature.violations.ViolationFsm
import com.haloxtraffic.feature.violations.ViolationThresholds

/**
 * Viewpoint-dependent FSMs (§6), enabled only when the junction geometry / mount supports them. They
 * share the base K-frame confirmation; seatbelt + phone are lower-confidence and are intended to be
 * corroborated by the VLM (Phase 7) before a challan-grade outcome — the COMMIT here flags a candidate.
 */

/** Red-light jump: signal RED and the tracked vehicle crosses the configured stop-line band. */
class RedLightJumpFsm(t: ViolationThresholds) :
    ViolationFsm(ViolationType.RED_LIGHT_JUMP, t.confirmFrames, t.rejectGapFrames) {
    override fun evaluate(observation: TrackObservation): Boolean =
        observation.signalRed && observation.crossedStopLine
}

/** No seatbelt (4-wheeler): driver region shows no seatbelt while the vehicle moves through frame. */
class NoSeatbeltFsm(t: ViolationThresholds) :
    ViolationFsm(ViolationType.NO_SEATBELT, t.confirmFrames, t.rejectGapFrames) {
    override fun evaluate(observation: TrackObservation): Boolean =
        observation.seatbeltAbsent
}

/** Mobile-phone use: a phone detection sits in the driver region of a moving vehicle. */
class PhoneUseFsm(t: ViolationThresholds) :
    ViolationFsm(ViolationType.PHONE_USE, t.confirmFrames, t.rejectGapFrames) {
    override fun evaluate(observation: TrackObservation): Boolean =
        observation.moving && observation.phoneNearDriver
}

/** Lane / line violation: sustained straddling of a configured lane boundary while moving. */
class LaneViolationFsm(t: ViolationThresholds) :
    ViolationFsm(ViolationType.LANE_VIOLATION, t.confirmFrames, t.rejectGapFrames) {
    override fun evaluate(observation: TrackObservation): Boolean =
        observation.moving && observation.laneStraddle
}
