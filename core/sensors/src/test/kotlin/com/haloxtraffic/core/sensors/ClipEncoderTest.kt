package com.haloxtraffic.core.sensors

import com.google.common.truth.Truth.assertThat
import com.haloxtraffic.core.sensors.camera.ClipEncoder
import org.junit.Test

class ClipEncoderTest {

    private fun solid(color: Int, w: Int, h: Int) = IntArray(w * h) { color }

    @Test fun `I420 output has the right size and Y plane`() {
        val w = 4; val h = 4
        val yuv = ClipEncoder.argbToI420(solid(0xFFFFFFFF.toInt(), w, h), w, h)
        assertThat(yuv.size).isEqualTo(w * h * 3 / 2)
        // White → Y ~ 235 (BT.601 full white maps near max luma).
        val y0 = yuv[0].toInt() and 0xFF
        assertThat(y0).isAtLeast(230)
    }

    @Test fun `NV12 output has interleaved UV plane size`() {
        val w = 4; val h = 4
        val yuv = ClipEncoder.argbToNv12(solid(0xFF000000.toInt(), w, h), w, h)
        assertThat(yuv.size).isEqualTo(w * h * 3 / 2)
        // Black → Y ~ 16 (BT.601 luma floor).
        assertThat(yuv[0].toInt() and 0xFF).isAtMost(20)
    }

    @Test fun `neutral gray sits near chroma center`() {
        val w = 2; val h = 2
        val yuv = ClipEncoder.argbToI420(solid(0xFF808080.toInt(), w, h), w, h)
        val u = yuv[w * h].toInt() and 0xFF
        val v = yuv[w * h + w * h / 4].toInt() and 0xFF
        assertThat(u).isWithin(4).of(128)
        assertThat(v).isWithin(4).of(128)
    }
}
