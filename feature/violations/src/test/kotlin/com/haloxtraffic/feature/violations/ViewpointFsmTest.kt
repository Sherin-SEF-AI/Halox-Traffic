package com.haloxtraffic.feature.violations

import com.google.common.truth.Truth.assertThat
import com.haloxtraffic.core.model.BoundingBox
import com.haloxtraffic.core.model.NormPoint
import com.haloxtraffic.core.model.Track
import com.haloxtraffic.core.model.VehicleClass
import com.haloxtraffic.core.model.ViolationType
import com.haloxtraffic.feature.violations.fsm.LaneViolationFsm
import com.haloxtraffic.feature.violations.fsm.NoSeatbeltFsm
import com.haloxtraffic.feature.violations.fsm.PhoneUseFsm
import com.haloxtraffic.feature.violations.fsm.RedLightJumpFsm
import org.junit.Test

class ViewpointFsmTest {

    private val t = ViolationThresholds() // confirmFrames = 5

    private fun obs(
        frame: Long,
        vc: VehicleClass = VehicleClass.CAR,
        signalRed: Boolean = false,
        crossedStopLine: Boolean = false,
        seatbeltAbsent: Boolean = false,
        phoneNearDriver: Boolean = false,
        laneStraddle: Boolean = false,
    ) = TrackObservation(
        frame = frame,
        track = Track(1, vc, BoundingBox(0.4f, 0.4f, 0.6f, 0.6f, 1f, 0), NormPoint(0f, 0f), 0, 0),
        moving = true, associatedPersons = 0, helmetedHeads = 0, unhelmetedHeads = 0,
        platePresent = true, plateReadable = true, plateConformant = null,
        expectedDirectionDeg = null, headingDeg = 0f,
        signalRed = signalRed, crossedStopLine = crossedStopLine,
        seatbeltAbsent = seatbeltAbsent, phoneNearDriver = phoneNearDriver, laneStraddle = laneStraddle,
    )

    @Test fun `red-light needs both red signal and stop-line crossing`() {
        val onlyRed = RedLightJumpFsm(t)
        repeat(6) { assertThat(onlyRed.onFrame(obs(it.toLong(), signalRed = true)).committed).isFalse() }

        val both = RedLightJumpFsm(t)
        var committed = false
        repeat(5) { committed = both.onFrame(obs(it.toLong(), signalRed = true, crossedStopLine = true)).committed }
        assertThat(committed).isTrue()
    }

    @Test fun `seatbelt, phone and lane commit on sustained cues`() {
        var sb = false; var ph = false; var ln = false
        val s = NoSeatbeltFsm(t); val p = PhoneUseFsm(t); val l = LaneViolationFsm(t)
        repeat(5) {
            sb = s.onFrame(obs(it.toLong(), seatbeltAbsent = true)).committed
            ph = p.onFrame(obs(it.toLong(), phoneNearDriver = true)).committed
            ln = l.onFrame(obs(it.toLong(), laneStraddle = true)).committed
        }
        assertThat(sb).isTrue()
        assertThat(ph).isTrue()
        assertThat(ln).isTrue()
    }

    @Test fun `engine only commits viewpoint types that are enabled`() {
        val enabled = ViolationEngine.DEFAULT_TYPES + ViolationType.RED_LIGHT_JUMP
        val engine = ViolationEngine(t, enabled)
        repeat(6) { f -> engine.process(listOf(obs(f.toLong(), signalRed = true, crossedStopLine = true, seatbeltAbsent = true))) }

        assertThat(engine.committedTypesFor(1)).contains(ViolationType.RED_LIGHT_JUMP)
        // NO_SEATBELT was not enabled → never committed even though the cue was present.
        assertThat(engine.committedTypesFor(1)).doesNotContain(ViolationType.NO_SEATBELT)
    }
}
