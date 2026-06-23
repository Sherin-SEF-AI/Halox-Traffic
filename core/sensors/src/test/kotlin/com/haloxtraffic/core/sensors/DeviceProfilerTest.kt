package com.haloxtraffic.core.sensors

import com.google.common.truth.Truth.assertThat
import com.haloxtraffic.core.model.DeviceTier
import com.haloxtraffic.core.sensors.profile.DeviceProfiler
import org.junit.Test

class DeviceProfilerTest {

    private val arm64 = listOf("arm64-v8a", "armeabi-v7a")
    private val arm32 = listOf("armeabi-v7a")

    @Test fun `high-end 64-bit with gpu is HIGH`() {
        assertThat(DeviceProfiler.assignTier(8192, gpuDelegate = true, nnapi = true, abis = arm64))
            .isEqualTo(DeviceTier.HIGH)
    }

    @Test fun `8GB without gpu falls to MID`() {
        assertThat(DeviceProfiler.assignTier(8192, gpuDelegate = false, nnapi = true, abis = arm64))
            .isEqualTo(DeviceTier.MID)
    }

    @Test fun `6GB with nnapi is MID`() {
        assertThat(DeviceProfiler.assignTier(6144, gpuDelegate = false, nnapi = true, abis = arm64))
            .isEqualTo(DeviceTier.MID)
    }

    @Test fun `4GB entry device is LOW`() {
        assertThat(DeviceProfiler.assignTier(4096, gpuDelegate = false, nnapi = true, abis = arm64))
            .isEqualTo(DeviceTier.LOW)
    }

    @Test fun `32-bit only device is held to LOW regardless of RAM`() {
        assertThat(DeviceProfiler.assignTier(8192, gpuDelegate = true, nnapi = true, abis = arm32))
            .isEqualTo(DeviceTier.LOW)
    }
}
