package com.haloxtraffic.feature.violations

import com.haloxtraffic.core.model.Track
import com.haloxtraffic.core.model.ViolationType

/**
 * Common FSM contract for violation detection (§6). Every violation type is a deterministic state
 * machine that must dwell in CONFIRMING for K frames meeting its criteria before COMMIT — this is what
 * keeps false positives low and makes the evidence defensible.
 *
 * States: IDLE → CANDIDATE → CONFIRMING → COMMITTED (+ REJECTED). One committed case per
 * (track_id, violation_type) per dwell.
 */
enum class FsmState { IDLE, CANDIDATE, CONFIRMING, COMMITTED, REJECTED }

/** A step of evidence in the FSM decision trace — the auditable "why". */
data class FsmTraceEntry(val frame: Long, val state: FsmState, val note: String)

/** Outcome of feeding one frame's observation to an FSM. */
data class FsmStep(val state: FsmState, val committed: Boolean, val trace: FsmTraceEntry)

/**
 * Everything a per-track FSM may need for one frame (§6/§7). Built by [ObservationBuilder] by
 * associating the frame's person/helmet/plate/signal detections to each vehicle [track]. Fields a given
 * violation doesn't use are simply ignored by its [ViolationFsm.evaluate].
 */
data class TrackObservation(
    val frame: Long,
    val track: Track,
    val moving: Boolean,
    /** Persons associated with this vehicle (for triple-riding). */
    val associatedPersons: Int,
    val helmetedHeads: Int,
    val unhelmetedHeads: Int,
    /** A plate was detected within the vehicle region. */
    val platePresent: Boolean,
    /** The plate detection is large/confident enough to be readable. */
    val plateReadable: Boolean,
    /** Plate conforms to a known Indian format; null until ANPR (Phase 4) reads it. */
    val plateConformant: Boolean?,
    /**
     * The vehicle is close/large enough that a plate would be reliably detectable if present. Absence
     * of a plate is only treated as evidence for vehicles we have a genuinely good look at, so a missed
     * detection on a distant or sharply angled vehicle is never read as a violation.
     */
    val vehicleProminent: Boolean = false,
    /** Expected travel direction in degrees (lane config or inferred majority flow), or null. */
    val expectedDirectionDeg: Float?,
    /** Track heading in degrees derived from velocity, or null if too slow to be meaningful. */
    val headingDeg: Float?,
    // Viewpoint-dependent cues (§6), populated only when junction geometry supports them.
    val signalRed: Boolean = false,
    val crossedStopLine: Boolean = false,
    /** 4-wheeler driver region shows no seatbelt (candidate; verified at high confidence/VLM). */
    val seatbeltAbsent: Boolean = false,
    /** A phone detection sits in the driver region. */
    val phoneNearDriver: Boolean = false,
    /** The vehicle straddles a configured lane boundary. */
    val laneStraddle: Boolean = false,
)

/**
 * Base class for per-track violation FSMs. Subclasses implement [evaluate]; this base handles the
 * debounce/confirmation bookkeeping uniformly so confirmation semantics are consistent and testable
 * across all violation types.
 *
 * @param confirmFrames K — consecutive criteria-meeting frames required before COMMIT.
 * @param rejectGapFrames consecutive non-meeting frames that reset CONFIRMING back toward IDLE.
 */
abstract class ViolationFsm(
    val type: ViolationType,
    private val confirmFrames: Int,
    private val rejectGapFrames: Int = 3,
) {
    var state: FsmState = FsmState.IDLE
        private set

    private var meetingStreak = 0
    private var gapStreak = 0
    private val trace = mutableListOf<FsmTraceEntry>()

    /** Per-subclass criteria check for the current observation. */
    abstract fun evaluate(observation: TrackObservation): Boolean

    fun onFrame(observation: TrackObservation): FsmStep {
        if (state == FsmState.COMMITTED || state == FsmState.REJECTED) {
            return FsmStep(state, committed = false, record(observation.frame, "terminal"))
        }
        val meets = evaluate(observation)
        if (meets) {
            meetingStreak++
            gapStreak = 0
            state = when {
                meetingStreak >= confirmFrames -> FsmState.COMMITTED
                meetingStreak == 1 -> FsmState.CANDIDATE
                else -> FsmState.CONFIRMING
            }
        } else {
            gapStreak++
            if (gapStreak >= rejectGapFrames) {
                meetingStreak = 0
                state = FsmState.IDLE
            }
        }
        val committed = state == FsmState.COMMITTED
        val note = if (meets) "criteria met ($meetingStreak/$confirmFrames)" else "gap ($gapStreak)"
        return FsmStep(state, committed, record(observation.frame, note))
    }

    fun traceSnapshot(): List<FsmTraceEntry> = trace.toList()

    private fun record(frame: Long, note: String): FsmTraceEntry =
        FsmTraceEntry(frame, state, note).also { trace += it }
}
