package com.haloxtraffic.feature.violations

import com.google.common.truth.Truth.assertThat
import com.haloxtraffic.core.model.BoundingBox
import com.haloxtraffic.core.model.NormPoint
import com.haloxtraffic.core.model.Track
import com.haloxtraffic.core.model.VehicleClass
import com.haloxtraffic.core.model.ViolationType
import com.haloxtraffic.feature.violations.fsm.NoHelmetFsm
import com.haloxtraffic.feature.violations.fsm.NoOrObscuredPlateFsm
import com.haloxtraffic.feature.violations.fsm.TripleRidingFsm
import com.haloxtraffic.feature.violations.fsm.WrongWayFsm
import org.junit.Test

class ViolationFsmTest {

    private val t = ViolationThresholds() // confirmFrames = 5

    private fun track(id: Long = 1, vc: VehicleClass = VehicleClass.MOTORCYCLE) =
        Track(id, vc, BoundingBox(0.4f, 0.4f, 0.6f, 0.6f, 1f, 0), NormPoint(0f, 0f), 0, 0)

    private fun obs(
        frame: Long,
        vc: VehicleClass = VehicleClass.MOTORCYCLE,
        moving: Boolean = true,
        persons: Int = 0,
        unhelmeted: Int = 0,
        platePresent: Boolean = true,
        plateReadable: Boolean = true,
        plateConformant: Boolean? = null,
        vehicleProminent: Boolean = true,
        mature: Boolean = true,
        expected: Float? = null,
        heading: Float? = null,
    ) = TrackObservation(
        frame = frame, track = track(vc = vc), moving = moving, associatedPersons = persons,
        helmetedHeads = 0, unhelmetedHeads = unhelmeted, platePresent = platePresent,
        plateReadable = plateReadable, plateConformant = plateConformant,
        vehicleProminent = vehicleProminent, trackMature = mature,
        expectedDirectionDeg = expected, headingDeg = heading,
    )

    @Test fun `no-helmet commits only after K sustained frames`() {
        val fsm = NoHelmetFsm(t)
        repeat(4) { assertThat(fsm.onFrame(obs(it.toLong(), unhelmeted = 1)).committed).isFalse() }
        assertThat(fsm.onFrame(obs(4, unhelmeted = 1)).committed).isTrue()
    }

    @Test fun `no-helmet resets after a sustained gap`() {
        val fsm = NoHelmetFsm(t)
        repeat(3) { fsm.onFrame(obs(it.toLong(), unhelmeted = 1)) }
        repeat(3) { fsm.onFrame(obs((10 + it).toLong(), unhelmeted = 0)) } // gap resets
        // Need a fresh K-run to commit; 4 more meeting frames must NOT yet commit.
        repeat(4) { assertThat(fsm.onFrame(obs((20 + it).toLong(), unhelmeted = 1)).committed).isFalse() }
        assertThat(fsm.onFrame(obs(24, unhelmeted = 1)).committed).isTrue()
    }

    @Test fun `triple riding commits at three associated persons`() {
        val fsm = TripleRidingFsm(t)
        repeat(5) { assertThat(fsm.onFrame(obs(it.toLong(), persons = 2)).committed).isFalse() }
        val fsm2 = TripleRidingFsm(t)
        var committed = false
        repeat(5) { committed = fsm2.onFrame(obs(it.toLong(), persons = 3)).committed }
        assertThat(committed).isTrue()
    }

    @Test fun `wrong-way needs both expected direction and opposing heading`() {
        val noExpected = WrongWayFsm(t)
        repeat(6) { assertThat(noExpected.onFrame(obs(it.toLong(), heading = 180f)).committed).isFalse() }

        val opposing = WrongWayFsm(t)
        var committed = false
        repeat(5) { committed = opposing.onFrame(obs(it.toLong(), expected = 0f, heading = 180f)).committed }
        assertThat(committed).isTrue()
    }

    @Test fun `no-plate commits when plate absent on a prominent moving vehicle`() {
        val fsm = NoOrObscuredPlateFsm(t)
        var committed = false
        repeat(5) { committed = fsm.onFrame(obs(it.toLong(), platePresent = false)).committed }
        assertThat(committed).isTrue()
    }

    @Test fun `no-plate does NOT fire on a distant vehicle (not prominent)`() {
        val fsm = NoOrObscuredPlateFsm(t)
        var committed = false
        repeat(10) { committed = fsm.onFrame(obs(it.toLong(), platePresent = false, vehicleProminent = false)).committed }
        assertThat(committed).isFalse()
    }

    @Test fun `no violation fires on a young (immature) track`() {
        // A momentary/flickering detection must never commit, regardless of criteria.
        val fsm = NoHelmetFsm(t)
        var committed = false
        repeat(12) { committed = fsm.onFrame(obs(it.toLong(), unhelmeted = 1, mature = false)).committed }
        assertThat(committed).isFalse()
    }

    @Test fun `no-plate does NOT fire just because a present plate was unreadable`() {
        // A plate we detected but could not cleanly OCR is our limitation, not a violation.
        val fsm = NoOrObscuredPlateFsm(t)
        var committed = false
        repeat(10) { committed = fsm.onFrame(obs(it.toLong(), platePresent = true, plateReadable = false)).committed }
        assertThat(committed).isFalse()
    }

    @Test fun `engine scopes FSMs by vehicle class and commits once`() {
        val engine = ViolationEngine(t)
        // A car with no plate, moving → PLATE violation, but never NO_HELMET (motorcycle-only).
        var events = emptyList<ViolationEvent>()
        repeat(6) { f ->
            events = engine.process(listOf(obs(f.toLong(), vc = VehicleClass.CAR, platePresent = false, unhelmeted = 1)))
        }
        assertThat(engine.committedTypesFor(1)).contains(ViolationType.PLATE_MISSING_OR_OBSCURED)
        assertThat(engine.committedTypesFor(1)).doesNotContain(ViolationType.NO_HELMET)

        // Further frames must not re-emit the same committed violation.
        val more = engine.process(listOf(obs(99, vc = VehicleClass.CAR, platePresent = false)))
        assertThat(more).isEmpty()
    }
}
