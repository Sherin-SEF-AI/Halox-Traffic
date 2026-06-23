package com.haloxtraffic.feature.anpr

import android.graphics.Bitmap
import android.graphics.Color
import com.haloxtraffic.core.model.PlateColor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Classifies plate background colour → vehicle category (§7F): white=private, yellow=commercial,
 * green=EV, black=self-drive rental, red=temporary, blue=diplomatic. Samples the plate border (away
 * from the central characters) and classifies the dominant hue/sat/value. Pure aside from pixel reads.
 */
@Singleton
class PlateColorClassifier @Inject constructor() {

    fun classify(crop: Bitmap): PlateColor {
        val w = crop.width
        val h = crop.height
        if (w < 4 || h < 4) return PlateColor.UNKNOWN

        var sumH = 0.0
        var sumS = 0.0
        var sumV = 0.0
        var count = 0
        val hsv = FloatArray(3)

        // Sample a grid, weighting the border band where the background colour dominates.
        val stepX = (w / SAMPLES).coerceAtLeast(1)
        val stepY = (h / SAMPLES).coerceAtLeast(1)
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val isBorder = y < h * 0.25 || y > h * 0.75 || x < w * 0.15 || x > w * 0.85
                if (isBorder) {
                    Color.colorToHSV(crop.getPixel(x, y), hsv)
                    sumH += hsv[0]; sumS += hsv[1]; sumV += hsv[2]; count++
                }
                x += stepX
            }
            y += stepY
        }
        if (count == 0) return PlateColor.UNKNOWN

        val hue = (sumH / count).toFloat()
        val sat = (sumS / count).toFloat()
        val value = (sumV / count).toFloat()

        return when {
            sat < 0.18f && value > 0.6f -> PlateColor.WHITE
            value < 0.22f -> PlateColor.BLACK
            sat < 0.25f -> PlateColor.WHITE
            hue < 18f || hue >= 340f -> PlateColor.RED
            hue in 40f..70f -> PlateColor.YELLOW
            hue in 70f..165f -> PlateColor.GREEN
            hue in 200f..260f -> PlateColor.BLUE
            else -> PlateColor.UNKNOWN
        }
    }

    private companion object {
        const val SAMPLES = 24
    }
}
