package com.haloxtraffic.feature.anpr

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

/** On-device plate-text recognizer contract (Stage 4). */
interface PlateOcrEngine {
    fun isReady(): Boolean

    /** Load the bundled recognizer. */
    fun init()

    /** Recognize the plate text in a [crop] → one [OcrRead] (the pipeline fuses N via [PlateConsensus]). */
    fun recognize(crop: Bitmap): OcrRead

    fun close()
}

/**
 * PP-OCRv5 (English, mobile) recognizer (§7C) run on ONNX Runtime. Input `x` [1,3,48,W], output
 * [1,T,438] CTC logits over the bundled dictionary (+blank at index 0). Preprocesses the plate crop to
 * 48×[WIDTH], normalises to [-1,1] (PaddleOCR convention), runs, and greedy-CTC-decodes via [CtcDecoder].
 * Never fabricates: not ready or an error → empty read, which the pipeline surfaces as uncertain.
 */
@Singleton
class OnnxOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlateOcrEngine {

    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var inputName: String = "x"
    private var decoder: CtcDecoder? = null

    override fun isReady(): Boolean = session != null

    override fun init() {
        close()
        val dict = context.assets.open(DICT_ASSET).bufferedReader().useLines { lines ->
            lines.map { it.firstOrNull() ?: ' ' }.toList()
        }
        decoder = CtcDecoder(dict, blankIndex = 0)
        val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
        val e = OrtEnvironment.getEnvironment()
        val s = e.createSession(bytes, OrtSession.SessionOptions())
        inputName = s.inputNames.firstOrNull() ?: "x"
        env = e
        session = s
        Timber.i("OnnxOcrEngine ready (PP-OCRv5 en, dict=${dict.size})")
    }

    override fun recognize(crop: Bitmap): OcrRead {
        val s = session ?: return OcrRead("", emptyList(), 0f)
        val e = env ?: return OcrRead("", emptyList(), 0f)
        val dec = decoder ?: return OcrRead("", emptyList(), 0f)
        return runCatching {
            OnnxTensor.createTensor(e, preprocess(crop), longArrayOf(1, 3, HEIGHT.toLong(), WIDTH.toLong())).use { t ->
                s.run(mapOf(inputName to t)).use { r ->
                    @Suppress("UNCHECKED_CAST")
                    val out = r[0].value as Array<Array<FloatArray>> // [1][T][C]
                    val grid = out[0]
                    val timeSteps = grid.size
                    val numClasses = grid[0].size
                    val flat = FloatArray(timeSteps * numClasses)
                    for (ti in 0 until timeSteps) System.arraycopy(grid[ti], 0, flat, ti * numClasses, numClasses)
                    dec.decode(flat, timeSteps, numClasses)
                }
            }
        }.getOrElse { Timber.e(it, "OCR failed"); OcrRead("", emptyList(), 0f) }
    }

    /** Plate crop → 48×WIDTH, NCHW, RGB, normalised to [-1,1]. */
    private fun preprocess(crop: Bitmap): FloatBuffer {
        val scaled = Bitmap.createScaledBitmap(crop, WIDTH, HEIGHT, true)
        val px = IntArray(WIDTH * HEIGHT)
        scaled.getPixels(px, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        if (scaled != crop) scaled.recycle()
        val plane = WIDTH * HEIGHT
        val buf = FloatBuffer.allocate(3 * plane)
        fun norm(v: Int) = (v / 255f - 0.5f) / 0.5f
        for (i in px.indices) buf.put(i, norm(px[i] shr 16 and 0xFF))
        for (i in px.indices) buf.put(plane + i, norm(px[i] shr 8 and 0xFF))
        for (i in px.indices) buf.put(2 * plane + i, norm(px[i] and 0xFF))
        return buf
    }

    override fun close() {
        runCatching { session?.close() }
        session = null
    }

    private companion object {
        const val MODEL_ASSET = "models/rec.onnx"
        const val DICT_ASSET = "models/ocr_dict.txt"
        const val HEIGHT = 48
        const val WIDTH = 320
    }
}
