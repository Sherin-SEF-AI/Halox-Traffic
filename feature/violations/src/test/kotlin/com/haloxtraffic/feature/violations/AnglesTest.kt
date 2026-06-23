package com.haloxtraffic.feature.violations

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AnglesTest {

    @Test fun `diff wraps around 360`() {
        assertThat(Angles.diff(10f, 350f)).isWithin(1e-3f).of(20f)
        assertThat(Angles.diff(0f, 180f)).isWithin(1e-3f).of(180f)
    }

    @Test fun `heading is null below min speed`() {
        assertThat(Angles.heading(0.001f, 0f, minSpeed = 0.01f)).isNull()
    }

    @Test fun `heading points along velocity`() {
        // +x is 0 degrees, +y is 90 (image coords, y down).
        assertThat(Angles.heading(1f, 0f, 0.01f)!!).isWithin(1f).of(0f)
        assertThat(Angles.heading(0f, 1f, 0.01f)!!).isWithin(1f).of(90f)
    }

    @Test fun `circular mean averages near-equal headings`() {
        val mean = Angles.circularMean(listOf(350f, 10f))!!
        // Mean of 350 and 10 wraps to ~0, not 180.
        assertThat(minOf(mean, 360f - mean)).isWithin(1f).of(0f)
    }
}
