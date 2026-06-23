package com.haloxtraffic.core.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Privacy: blurs detected faces in an exported still (§11/§14), detection only — no recognition, fully
 * on-device (ML Kit). Applied to a COPY at export time; the sealed original is never modified. Faces are
 * mosaicked (down/up-scale) rather than blacked out so the scene stays legible.
 */
@Singleton
class FaceBlurrer @Inject constructor() {

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build(),
        )
    }

    /** Returns a copy with faces mosaicked, or the input unchanged if none found / detection fails. */
    suspend fun blurFaces(src: Bitmap): Bitmap {
        val faces = runCatching { detector.process(InputImage.fromBitmap(src, 0)).await() }
            .getOrElse { Timber.w(it, "Face detection failed; exporting unblurred"); return src }
        if (faces.isEmpty()) return src

        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        faces.forEach { face -> mosaic(out, canvas, face.boundingBox) }
        return out
    }

    private fun mosaic(bitmap: Bitmap, canvas: Canvas, rawRect: Rect) {
        val rect = Rect(
            rawRect.left.coerceIn(0, bitmap.width - 1),
            rawRect.top.coerceIn(0, bitmap.height - 1),
            rawRect.right.coerceIn(1, bitmap.width),
            rawRect.bottom.coerceIn(1, bitmap.height),
        )
        if (rect.width() < 4 || rect.height() < 4) return
        val face = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
        val tiny = Bitmap.createScaledBitmap(face, MOSAIC, MOSAIC, false)
        val blocky = Bitmap.createScaledBitmap(tiny, rect.width(), rect.height(), false)
        canvas.drawBitmap(blocky, rect.left.toFloat(), rect.top.toFloat(), null)
        face.recycle(); tiny.recycle(); blocky.recycle()
    }

    private companion object {
        const val MOSAIC = 8
    }
}
