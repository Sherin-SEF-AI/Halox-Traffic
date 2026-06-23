package com.haloxtraffic.feature.anpr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.common.truth.Truth.assertThat
import com.haloxtraffic.core.model.PlateColor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlateColorClassifierTest {

    private val classifier = PlateColorClassifier()

    private fun solid(color: Int, w: Int = 64, h: Int = 24): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        return bmp
    }

    @Test fun `white plate classifies as private`() {
        val c = classifier.classify(solid(Color.WHITE))
        assertThat(c).isEqualTo(PlateColor.WHITE)
        assertThat(c.category).isEqualTo("private")
    }

    @Test fun `yellow plate classifies as commercial`() {
        assertThat(classifier.classify(solid(Color.rgb(255, 214, 0)))).isEqualTo(PlateColor.YELLOW)
    }

    @Test fun `green plate classifies as electric`() {
        assertThat(classifier.classify(solid(Color.rgb(0, 150, 60)))).isEqualTo(PlateColor.GREEN)
    }

    @Test fun `black plate classifies as self-drive rental`() {
        assertThat(classifier.classify(solid(Color.BLACK))).isEqualTo(PlateColor.BLACK)
    }
}
