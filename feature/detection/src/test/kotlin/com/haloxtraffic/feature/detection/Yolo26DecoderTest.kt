package com.haloxtraffic.feature.detection

import com.google.common.truth.Truth.assertThat
import com.haloxtraffic.feature.detection.model.BoxFormat
import com.haloxtraffic.feature.detection.model.DetectorOutputLayout
import com.haloxtraffic.feature.detection.runtime.DelegateSelector
import com.haloxtraffic.feature.detection.runtime.Yolo26Decoder
import com.haloxtraffic.core.model.DetectionConfig
import com.haloxtraffic.core.model.DeviceTier
import com.haloxtraffic.core.model.InferenceDelegate
import org.junit.Test

class Yolo26DecoderTest {

    private val layout = DetectorOutputLayout(
        numDetections = 3,
        attributesPerBox = 6,
        boxFormat = BoxFormat.XYWH,
        coordsNormalized = true,
        classNames = listOf("car"),
    )

    @Test fun `decodes XYWH boxes above threshold and drops the rest`() {
        // box0: centered, score 0.9, class 0; box1: score 0.1 (dropped); box2: score 0.5, class 1
        val output = floatArrayOf(
            0.5f, 0.5f, 0.2f, 0.2f, 0.9f, 0f,
            0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0f,
            0.8f, 0.8f, 0.4f, 0.4f, 0.5f, 1f,
        )
        val boxes = Yolo26Decoder.decode(output, layout, scoreThreshold = 0.25f)
        assertThat(boxes).hasSize(2)
        val b0 = boxes[0]
        assertThat(b0.classId).isEqualTo(0)
        assertThat(b0.left).isWithin(1e-4f).of(0.4f)
        assertThat(b0.right).isWithin(1e-4f).of(0.6f)
        assertThat(b0.centerX).isWithin(1e-4f).of(0.5f)
    }

    @Test fun `coordinates are clamped to 0_1`() {
        val output = floatArrayOf(0.95f, 0.95f, 0.5f, 0.5f, 0.9f, 0f, 0f,0f,0f,0f,0f,0f, 0f,0f,0f,0f,0f,0f)
        val boxes = Yolo26Decoder.decode(output, layout)
        assertThat(boxes.first().right).isAtMost(1f)
        assertThat(boxes.first().bottom).isAtMost(1f)
    }

    @Test fun `delegate chain always ends with CPU fallback`() {
        val low = DelegateSelector.chain(DetectionConfig.forTier(DeviceTier.LOW))
        assertThat(low.last()).isEqualTo(InferenceDelegate.XNNPACK_CPU)

        val high = DelegateSelector.chain(DetectionConfig.forTier(DeviceTier.HIGH))
        assertThat(high.first()).isEqualTo(InferenceDelegate.GPU)
        assertThat(high).contains(InferenceDelegate.XNNPACK_CPU)
    }
}
