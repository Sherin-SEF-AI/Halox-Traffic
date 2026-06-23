package com.haloxtraffic.feature.violations

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
 * Base class for per-track violation FSMs. Subclasses implement [evaluate] to decide whether the
 * current frame meets the violation criteria; this base handles the debounce/confirmation bookkeeping
 * uniformly so confirmation semantics are consistent and unit-testable across all violation types.
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

    /** Per-subclass criteria check for the current observation. Implemented in Phase 3. */
    abstract fun evaluate(observation: Observation): Boolean

    fun onFrame(observation: Observation): FsmStep {
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
        return FsmStep(state, committed, record(observation.frame, if (meets) "criteria met ($meetingStreak/$confirmFrames)" else "gap ($gapStreak)"))
    }

    fun traceSnapshot(): List<FsmTraceEntry> = trace.toList()

    private fun record(frame: Long, note: String): FsmTraceEntry =
        FsmTraceEntry(frame, state, note).also { trace += it }

    /**
     * Per-frame observation fed to FSMs (filled by tracking + detection in Phase 3). Kept generic here;
     * concrete fields (heads, helmet flags, occupant count, direction, signal state, stop-line crossing)
     * are added as the detectors that produce them land.
     */
    data class Observation(
        val frame: Long,
        val trackId: Long,
        val moving: Boolean,
        val signals: Map<String, Float> = emptyMap(),
    )
}
