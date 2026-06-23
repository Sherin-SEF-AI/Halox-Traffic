package com.haloxtraffic.feature.detection.runtime

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.camera.core.ImageProxy
import javax.inject.Inject
import javax.inject.Singleton

/** A square model input bitmap plus the transform to map detections back to the upright frame. */
data class PreprocessedFrame(
    val bitmap: Bitmap,
    val transform: LetterboxTransform,
    /** Upright (display-oriented) frame dimensions, for overlay aspect reference. */
    val uprightWidth: Int,
    val uprightHeight: Int,
)

/**
 * Turns a YUV [ImageProxy] into the detector's square input (§3 frame gate / §4 best-frame): convert
 * to RGB, rotate to upright using the frame's rotation, then aspect-preserving letterbox into
 * [inputSize]². The recorded [LetterboxTransform] lets the overlay invert detections precisely.
 */
@Singleton
class FramePreprocessor @Inject constructor() {

    private val filterPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun process(image: ImageProxy, inputSize: Int): PreprocessedFrame {
        val argb = YuvToRgbConverter.toArgb(image)
        val src = Bitmap.createBitmap(argb, image.width, image.height, Bitmap.Config.ARGB_8888)

        val rotation = image.imageInfo.rotationDegrees
        val upright = if (rotation == 0) {
            src
        } else {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true).also { if (it != src) src.recycle() }
        }

        val transform = LetterboxTransform.forFrame(upright.width, upright.height, inputSize)
        val dst = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
        Canvas(dst).apply {
            drawColor(Color.BLACK)
            val drawMatrix = Matrix().apply {
                postScale(transform.contentW / upright.width, transform.contentH / upright.height)
                postTranslate(transform.offsetX, transform.offsetY)
            }
            drawBitmap(upright, drawMatrix, filterPaint)
        }
        val uw = upright.width
        val uh = upright.height
        if (upright != src) upright.recycle()

        return PreprocessedFrame(dst, transform, uw, uh)
    }
}
