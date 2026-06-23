package com.haloxtraffic.core.sensors

import com.google.common.truth.Truth.assertThat
import com.haloxtraffic.core.model.DeviceTier
import com.haloxtraffic.core.sensors.profile.AdaptiveRuntimeController
import org.junit.Test

class AdaptiveRuntimeControllerTest {

    @Test fun `over-budget latency drops fps first, never below floor`() {
        val c = AdaptiveRuntimeController().apply { reset(DeviceTier.HIGH) }
        val startFps = c.config.value.targetFps

        c.report(avgLatencyMs = 500, thermalHeadroom = 0f) // way over budget
        assertThat(c.config.value.targetFps).isLessThan(startFps)
        assertThat(c.degraded.value).isTrue()

        // Keep hammering — fps floors, then resolution drops, vlm disables; never crashes.
        repeat(20) { c.report(avgLatencyMs = 500, thermalHeadroom = 1f) }
        assertThat(c.config.value.targetFps).isAtLeast(AdaptiveRuntimeController.MIN_FPS)
        assertThat(c.config.value.vlmEnabled).isFalse()
    }

    @Test fun `recovers back toward baseline when headroom returns`() {
        val c = AdaptiveRuntimeController().apply { reset(DeviceTier.HIGH) }
        val baseline = c.config.value
        repeat(10) { c.report(avgLatencyMs = 500, thermalHeadroom = 1f) }
        assertThat(c.config.value).isNotEqualTo(baseline)

        repeat(30) { c.report(avgLatencyMs = 5, thermalHeadroom = 0f) }
        assertThat(c.config.value).isEqualTo(baseline)
        assertThat(c.degraded.value).isFalse()
    }
}
