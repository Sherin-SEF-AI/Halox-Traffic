package com.haloxtraffic.feature.anpr

import android.graphics.Bitmap

/** Bitmap helpers for the ANPR pipeline. */
object ImageOps {

    /** Row-major luma (0..255) for [SharpnessScorer]. */
    fun toGray(bitmap: Bitmap): Triple<FloatArray, Int, Int> {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = FloatArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }
        return Triple(gray, w, h)
    }

    /** Sharpness score (variance of Laplacian) for a crop — higher is sharper. */
    fun sharpness(bitmap: Bitmap): Double {
        val (gray, w, h) = toGray(bitmap)
        return SharpnessScorer.varianceOfLaplacian(gray, w, h)
    }
}
