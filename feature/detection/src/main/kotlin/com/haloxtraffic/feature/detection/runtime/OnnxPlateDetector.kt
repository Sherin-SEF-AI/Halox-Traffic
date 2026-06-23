package com.haloxtraffic.feature.detection.runtime

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.haloxtraffic.core.model.BoundingBox
import com.haloxtraffic.core.model.DetectionClass
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * License-plate detector: a bundled YOLOv11 ONNX model run via ONNX Runtime (input `images`
 * [1,3,S,S], output `output0` [1,5,N] = cx,cy,w,h,score). Decodes + NMS in Kotlin and returns plate
 * boxes (normalised to the input square) tagged as [DetectionClass.PLATE]. Runs alongside the COCO
 * detector (see [CompositeDetector]). Fail-soft: any error yields no plate boxes.
 */
@Singleton
class OnnxPlateDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var inputName: String = "images"

    fun isReady(): Boolean = session != null

    fun init(inputSize: Int) {
        close()
        this.size = inputSize
        val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
        val e = OrtEnvironment.getEnvironment()
        val s = e.createSession(bytes, OrtSession.SessionOptions())
        inputName = s.inputNames.firstOrNull() ?: "images"
        env = e
        session = s
        Timber.i("OnnxPlateDetector ready (YOLOv11 license plate, in=$inputSize)")
    }

    private var size: Int = 640

    /** @return plate boxes normalised to the [input] square. */
    fun detect(input: Bitmap, scoreThreshold: Float = 0.35f): List<BoundingBox> {
        val s = session ?: return emptyList()
        val e = env ?: return emptyList()
        return runCatching {
            val tensor = OnnxTensor.createTensor(e, preprocess(input), longArrayOf(1, 3, size.toLong(), size.toLong()))
            tensor.use {
                s.run(mapOf(inputName to it)).use { results ->
                    @Suppress("UNCHECKED_CAST")
                    val out = results[0].value as Array<Array<FloatArray>> // [1][5][N]
                    decode(out[0], scoreThreshold)
                }
            }
        }.getOrElse { Timber.e(it, "Plate inference failed"); emptyList() }
    }

    /** Bitmap → NCHW float [1,3,S,S], RGB, normalised 0..1 (ultralytics convention). */
    private fun preprocess(bitmap: Bitmap): FloatBuffer {
        val scaled = if (bitmap.width == size && bitmap.height == size) bitmap
        else Bitmap.createScaledBitmap(bitmap, size, size, true)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        if (scaled != bitmap) scaled.recycle()

        val buf = FloatBuffer.allocate(3 * size * size)
        val plane = size * size
        // Planar: all R, then G, then B.
        for (i in pixels.indices) buf.put(i, ((pixels[i] shr 16 and 0xFF) / 255f))
        for (i in pixels.indices) buf.put(plane + i, ((pixels[i] shr 8 and 0xFF) / 255f))
        for (i in pixels.indices) buf.put(2 * plane + i, ((pixels[i] and 0xFF) / 255f))
        return buf
    }

    /** YOLOv11 raw output [5][N] (cx,cy,w,h,score in input px) → NMS'd normalised boxes. */
    private fun decode(out: Array<FloatArray>, scoreThreshold: Float): List<BoundingBox> {
        val n = out[0].size
        val cand = ArrayList<BoundingBox>()
        for (i in 0 until n) {
            val score = out[4][i]
            if (score < scoreThreshold) continue
            val cx = out[0][i]; val cy = out[1][i]; val w = out[2][i]; val h = out[3][i]
            cand += BoundingBox(
                left = ((cx - w / 2f) / size).coerceIn(0f, 1f),
                top = ((cy - h / 2f) / size).coerceIn(0f, 1f),
                right = ((cx + w / 2f) / size).coerceIn(0f, 1f),
                bottom = ((cy + h / 2f) / size).coerceIn(0f, 1f),
                score = score,
                classId = DetectionClass.PLATE.ordinal,
            )
        }
        return nms(cand, IOU_THRESHOLD)
    }

    private fun nms(boxes: List<BoundingBox>, iouThr: Float): List<BoundingBox> {
        val sorted = boxes.sortedByDescending { it.score }.toMutableList()
        val kept = ArrayList<BoundingBox>()
        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            kept += best
            sorted.removeAll { best.iou(it) > iouThr }
        }
        return kept
    }

    fun close() {
        runCatching { session?.close() }
        session = null
    }

    private companion object {
        const val MODEL_ASSET = "models/plate.onnx"
        const val IOU_THRESHOLD = 0.45f
    }
}
