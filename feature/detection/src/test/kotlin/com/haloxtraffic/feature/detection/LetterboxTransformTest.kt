package com.haloxtraffic.feature.detection

import com.google.common.truth.Truth.assertThat
import com.haloxtraffic.core.model.BoundingBox
import com.haloxtraffic.feature.detection.runtime.LetterboxTransform
import org.junit.Test

class LetterboxTransformTest {

    @Test fun `landscape frame letterboxes with vertical padding`() {
        val t = LetterboxTransform.forFrame(uprightW = 1920, uprightH = 1080, inputSize = 640)
        assertThat(t.contentW).isWithin(1e-3f).of(640f)
        assertThat(t.contentH).isWithin(1e-3f).of(360f)
        assertThat(t.offsetX).isWithin(1e-3f).of(0f)
        assertThat(t.offsetY).isWithin(1e-3f).of(140f)
    }

    @Test fun `center of square maps back to center of frame`() {
        val t = LetterboxTransform.forFrame(1920, 1080, 640)
        val box = BoundingBox(0.45f, 0.45f, 0.55f, 0.55f, score = 0.9f, classId = 0)
        val inv = t.invert(box)
        assertThat(inv.centerX).isWithin(1e-3f).of(0.5f)
        assertThat(inv.centerY).isWithin(1e-3f).of(0.5f)
    }

    @Test fun `content-region top edge maps to frame top`() {
        val t = LetterboxTransform.forFrame(1920, 1080, 640)
        // squareNorm y for the padding edge = offsetY / inputSize.
        val edge = t.offsetY / t.inputSize
        val box = BoundingBox(0f, edge, 1f, edge + 0.1f, 0.9f, 0)
        assertThat(t.invert(box).top).isWithin(1e-3f).of(0f)
    }

    @Test fun `portrait frame pads horizontally`() {
        val t = LetterboxTransform.forFrame(1080, 1920, 640)
        assertThat(t.contentW).isWithin(1e-3f).of(360f)
        assertThat(t.contentH).isWithin(1e-3f).of(640f)
        assertThat(t.offsetX).isWithin(1e-3f).of(140f)
        assertThat(t.offsetY).isWithin(1e-3f).of(0f)
    }

    @Test fun `inverted coordinates stay within 0_1`() {
        val t = LetterboxTransform.forFrame(1920, 1080, 640)
        val box = BoundingBox(0f, 0f, 1f, 1f, 0.9f, 0)
        val inv = t.invert(box)
        assertThat(inv.left).isAtLeast(0f)
        assertThat(inv.bottom).isAtMost(1f)
    }

    @Test fun `forward then invert round-trips a content-region box`() {
        val t = LetterboxTransform.forFrame(1920, 1080, 640)
        // A box well inside the content region (upright-normalised).
        val upright = BoundingBox(0.3f, 0.45f, 0.5f, 0.55f, 0.9f, 8)
        val square = t.forward(upright)
        val back = t.invert(square)
        assertThat(back.left).isWithin(1e-3f).of(upright.left)
        assertThat(back.top).isWithin(1e-3f).of(upright.top)
        assertThat(back.right).isWithin(1e-3f).of(upright.right)
        assertThat(back.bottom).isWithin(1e-3f).of(upright.bottom)
    }
}
