package com.haloxtraffic.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DetectionConfigTest {

    @Test fun `LOW tier disables VLM and runs CPU only`() {
        val c = DetectionConfig.forTier(DeviceTier.LOW)
        assertThat(c.vlmEnabled).isFalse()
        assertThat(c.quantization).isEqualTo(Quantization.INT8)
        assertThat(c.delegateChain).containsExactly(InferenceDelegate.XNNPACK_CPU)
    }

    @Test fun `MID tier uses accelerator chain and INT8`() {
        val c = DetectionConfig.forTier(DeviceTier.MID)
        assertThat(c.vlmEnabled).isFalse()
        assertThat(c.delegateChain.first()).isAnyOf(InferenceDelegate.NNAPI, InferenceDelegate.GPU)
        assertThat(c.delegateChain).contains(InferenceDelegate.XNNPACK_CPU)
    }

    @Test fun `HIGH tier enables VLM and prefers GPU`() {
        val c = DetectionConfig.forTier(DeviceTier.HIGH)
        assertThat(c.vlmEnabled).isTrue()
        assertThat(c.delegateChain.first()).isEqualTo(InferenceDelegate.GPU)
        assertThat(c.targetFps).isAtLeast(DetectionConfig.forTier(DeviceTier.LOW).targetFps)
    }

    @Test fun `high-confidence violations exclude viewpoint-dependent ones`() {
        val hc = ViolationType.highConfidence
        assertThat(hc).contains(ViolationType.NO_HELMET)
        assertThat(hc).doesNotContain(ViolationType.RED_LIGHT_JUMP)
        assertThat(hc.none { it.viewpointDependent }).isTrue()
    }
}
