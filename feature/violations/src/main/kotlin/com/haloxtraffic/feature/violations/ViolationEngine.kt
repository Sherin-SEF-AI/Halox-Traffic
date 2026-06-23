package com.haloxtraffic.feature.violations

import com.haloxtraffic.core.model.Track
import com.haloxtraffic.core.model.VehicleClass
import com.haloxtraffic.core.model.ViolationType
import com.haloxtraffic.feature.violations.fsm.NoHelmetFsm
import com.haloxtraffic.feature.violations.fsm.NoOrObscuredPlateFsm
import com.haloxtraffic.feature.violations.fsm.TripleRidingFsm
import com.haloxtraffic.feature.violations.fsm.WrongWayFsm

/** A committed violation (§6). Carries the FSM decision trace — the auditable "why". */
data class ViolationEvent(
    val trackId: Long,
    val type: ViolationType,
    val frame: Long,
    val track: Track,
    val trace: List<FsmTraceEntry>,
)

/**
 * Runs the per-track FSM bank (§6). Each track gets one FSM per enabled, applicable violation type;
 * observations are fed in each frame and the first COMMIT per (track, type) is emitted exactly once.
 * Deterministic and unit-testable.
 */
class ViolationEngine(
    private val thresholds: ViolationThresholds = ViolationThresholds(),
    private val enabledTypes: Set<ViolationType> = DEFAULT_TYPES,
) {
    private val perTrack = HashMap<Long, Map<ViolationType, ViolationFsm>>()
    private val committed = HashSet<Pair<Long, ViolationType>>()

    fun process(observations: List<TrackObservation>): List<ViolationEvent> {
        val events = ArrayList<ViolationEvent>()
        for (obs in observations) {
            val fsms = perTrack.getOrPut(obs.track.trackId) { createFsms(obs.track.vehicleClass) }
            for ((type, fsm) in fsms) {
                val key = obs.track.trackId to type
                if (key in committed) continue
                if (fsm.onFrame(obs).committed) {
                    committed += key
                    events += ViolationEvent(obs.track.trackId, type, obs.frame, obs.track, fsm.traceSnapshot())
                }
            }
        }
        return events
    }

    /** Violation types already committed for a track — used to colour its overlay. */
    fun committedTypesFor(trackId: Long): Set<ViolationType> =
        committed.filter { it.first == trackId }.map { it.second }.toSet()

    fun reset() {
        perTrack.clear()
        committed.clear()
    }

    private fun createFsms(vehicleClass: VehicleClass): Map<ViolationType, ViolationFsm> =
        enabledTypes
            .filter { vehicleClass in it.appliesTo }
            .associateWith { type ->
                when (type) {
                    ViolationType.NO_HELMET -> NoHelmetFsm(thresholds)
                    ViolationType.TRIPLE_RIDING -> TripleRidingFsm(thresholds)
                    ViolationType.WRONG_WAY -> WrongWayFsm(thresholds)
                    ViolationType.PLATE_MISSING_OR_OBSCURED -> NoOrObscuredPlateFsm(thresholds)
                    else -> error("No FSM wired for $type (lands in a later phase)")
                }
            }

    companion object {
        /** Phase-3 high-confidence types. Red-light/seatbelt/phone/lane land in Phase 6. */
        val DEFAULT_TYPES = setOf(
            ViolationType.NO_HELMET,
            ViolationType.TRIPLE_RIDING,
            ViolationType.WRONG_WAY,
            ViolationType.PLATE_MISSING_OR_OBSCURED,
        )
    }
}
