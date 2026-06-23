package com.haloxtraffic.feature.detection.runtime

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.haloxtraffic.core.model.BoundingBox
import com.haloxtraffic.core.model.DetectionClass
import timber.log.Timber
import java.nio.FloatBuffer

/**
 * Generic YOLOv8/v11 detector on ONNX Runtime: input `images[1,3,S,S]`, output `output0[1,4+nc,N]`.
 * Decodes (per-anchor argmax over the nc class scores), NMS, and maps class indices to [DetectionClass]
 * via [classMap]. Returns boxes normalised to the input bitmap — the caller (CompositeDetector) runs it
 * on full frames or on vehicle crops and remaps. Fail-soft: any error → no boxes.
 */
class OnnxYoloDetector(
    private val context: Context,
    private val assetPath: String,
    private val inputSize: Int,
    private val classMap: Map<Int, DetectionClass>,
    private val scoreThreshold: Float = 0.35f,
    private val iouThreshold: Float = 0.45f,
) {
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var inputName: String = "images"

    fun isReady(): Boolean = session != null

    fun init() {
        close()
        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        val e = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions().apply {
            // Use available CPU cores; NNAPI EP is added in the perf pass.
            setIntraOpNumThreads(java.lang.Runtime.getRuntime().availableProcessors().coerceAtMost(4))
        }
        val s = e.createSession(bytes, opts)
        inputName = s.inputNames.firstOrNull() ?: "images"
        env = e
        session = s
        Timber.i("OnnxYoloDetector ready ($assetPath, in=$inputSize, classes=${classMap.values})")
    }

    /** @return boxes normalised to [input] (after internal resize to [inputSize]). */
    fun detect(input: Bitmap): List<BoundingBox> {
        val s = session ?: return emptyList()
        val e = env ?: return emptyList()
        return runCatching {
            OnnxTensor.createTensor(e, preprocess(input), longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())).use { t ->
                s.run(mapOf(inputName to t)).use { r ->
                    @Suppress("UNCHECKED_CAST")
                    val out = r[0].value as Array<Array<FloatArray>> // [1][4+nc][N]
                    decode(out[0])
                }
            }
        }.getOrElse { Timber.e(it, "ONNX detect failed ($assetPath)"); emptyList() }
    }

    private fun preprocess(bitmap: Bitmap): FloatBuffer {
        val scaled = if (bitmap.width == inputSize && bitmap.height == inputSize) bitmap
        else Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val px = IntArray(inputSize * inputSize)
        scaled.getPixels(px, 0, inputSize, 0, 0, inputSize, inputSize)
        if (scaled != bitmap) scaled.recycle()
        val plane = inputSize * inputSize
        val buf = FloatBuffer.allocate(3 * plane)
        for (i in px.indices) buf.put(i, (px[i] shr 16 and 0xFF) / 255f)
        for (i in px.indices) buf.put(plane + i, (px[i] shr 8 and 0xFF) / 255f)
        for (i in px.indices) buf.put(2 * plane + i, (px[i] and 0xFF) / 255f)
        return buf
    }

    /** out = [4+nc][N]; rows 0..3 = cx,cy,w,h (px), rows 4.. = per-class scores. */
    private fun decode(out: Array<FloatArray>): List<BoundingBox> {
        val rows = out.size
        val nc = rows - 4
        val n = out[0].size
        val cand = ArrayList<BoundingBox>()
        for (i in 0 until n) {
            var bestC = 0
            var bestS = out[4][i]
            for (c in 1 until nc) {
                val sc = out[4 + c][i]
                if (sc > bestS) { bestS = sc; bestC = c }
            }
            if (bestS < scoreThreshold) continue
            val cls = classMap[bestC] ?: continue
            val cx = out[0][i]; val cy = out[1][i]; val w = out[2][i]; val h = out[3][i]
            cand += BoundingBox(
                left = ((cx - w / 2f) / inputSize).coerceIn(0f, 1f),
                top = ((cy - h / 2f) / inputSize).coerceIn(0f, 1f),
                right = ((cx + w / 2f) / inputSize).coerceIn(0f, 1f),
                bottom = ((cy + h / 2f) / inputSize).coerceIn(0f, 1f),
                score = bestS,
                classId = cls.ordinal,
            )
        }
        return nms(cand)
    }

    private fun nms(boxes: List<BoundingBox>): List<BoundingBox> {
        val sorted = boxes.sortedByDescending { it.score }.toMutableList()
        val kept = ArrayList<BoundingBox>()
        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            kept += best
            sorted.removeAll { best.iou(it) > iouThreshold }
        }
        return kept
    }

    fun close() {
        runCatching { session?.close() }
        session = null
    }
}
