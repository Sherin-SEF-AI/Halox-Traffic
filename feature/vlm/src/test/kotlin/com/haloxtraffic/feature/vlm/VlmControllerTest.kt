package com.haloxtraffic.feature.vlm

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.haloxtraffic.core.model.DeviceTier
import com.haloxtraffic.core.model.ViolationType
import com.haloxtraffic.feature.detection.model.ModelProvisioner
import com.haloxtraffic.feature.detection.model.ModelRegistry
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VlmControllerTest {

    /** Engine that is "ready" but should never be reached unless the controller itself is ready. */
    private class FakeEngine : VlmEngine {
        var generated = 0
        override fun isReady() = true
        override fun init(modelFile: File, useGpu: Boolean) = Unit
        override fun generate(prompt: String, image: Bitmap?): String? { generated++; return "YES" }
        override fun close() = Unit
    }

    private val dispatcher = UnconfinedTestDispatcher()
    private val engine = FakeEngine()

    private fun controller() = VlmController(
        engine = engine,
        provisioner = ModelProvisioner(ApplicationProvider.getApplicationContext(), dispatcher, OkHttpClient()),
        registry = ModelRegistry(),
        io = dispatcher,
    )

    @Test fun `disabled on non-HIGH tier`() = runTest {
        val vlm = controller()
        assertThat(vlm.start(DeviceTier.LOW, vlmEnabled = true)).isFalse()
        assertThat(vlm.status.value).isEqualTo(VlmStatus.DISABLED)
        assertThat(vlm.isReady()).isFalse()
    }

    @Test fun `disabled when vlm flag is off even on HIGH`() = runTest {
        val vlm = controller()
        assertThat(vlm.start(DeviceTier.HIGH, vlmEnabled = false)).isFalse()
        assertThat(vlm.status.value).isEqualTo(VlmStatus.DISABLED)
    }

    @Test fun `requests are no-ops and never call the engine when not ready`() = runTest {
        val vlm = controller()
        val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        assertThat(vlm.verifyViolation(ViolationType.RED_LIGHT_JUMP, bmp)).isNull()
        assertThat(vlm.readPlate(bmp)).isNull()
        assertThat(vlm.describe(ViolationType.NO_HELMET, "KA05MH2453", bmp)).isNull()
        assertThat(engine.generated).isEqualTo(0) // gated off before reaching the engine
    }
}
