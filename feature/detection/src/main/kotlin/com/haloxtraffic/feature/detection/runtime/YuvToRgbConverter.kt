package com.haloxtraffic.feature.detection.runtime

import androidx.camera.core.ImageProxy

/**
 * Converts a CameraX YUV_420_888 [ImageProxy] to a packed ARGB int array (§10). Handles arbitrary
 * pixel/row strides (devices pad rows and may interleave U/V), so it is correct across hardware rather
 * than assuming a tight layout. Pure aside from reading the image planes — no Android graphics deps.
 */
object YuvToRgbConverter {

    /** @return ARGB_8888 pixels, row-major, length == width*height. */
    fun toArgb(image: ImageProxy): IntArray {
        val width = image.width
        val height = image.height
        val out = IntArray(width * height)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixStride = yPlane.pixelStride
        val uvRowStride = uPlane.rowStride
        val uvPixStride = uPlane.pixelStride // U and V share stride layout

        for (row in 0 until height) {
            val yRow = row * yRowStride
            val uvRow = (row shr 1) * uvRowStride
            for (col in 0 until width) {
                val y = (yBuffer.get(yRow + col * yPixStride).toInt() and 0xFF)
                val uvCol = (col shr 1) * uvPixStride
                val u = (uBuffer.get(uvRow + uvCol).toInt() and 0xFF) - 128
                val v = (vBuffer.get(uvRow + uvCol).toInt() and 0xFF) - 128

                // BT.601 full-range YUV → RGB.
                val yy = y * 1.164f - 18.624f
                var r = (yy + 1.596f * v).toInt()
                var g = (yy - 0.391f * u - 0.813f * v).toInt()
                var b = (yy + 2.018f * u).toInt()
                r = if (r < 0) 0 else if (r > 255) 255 else r
                g = if (g < 0) 0 else if (g > 255) 255 else g
                b = if (b < 0) 0 else if (b > 255) 255 else b

                out[row * width + col] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return out
    }
}
