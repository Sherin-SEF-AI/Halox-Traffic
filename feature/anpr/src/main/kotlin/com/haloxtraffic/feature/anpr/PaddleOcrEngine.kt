package com.haloxtraffic.feature.anpr

import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

/** On-device plate-text recognizer contract (Stage 4). */
interface PlateOcrEngine {
    fun isReady(): Boolean
    fun init(modelFile: File)

    /**
     * Recognize the plate text in a [crop]. Returns a single [OcrRead]; the pipeline calls this on the
     * top-N sharpest crops and fuses the results via [PlateConsensus].
     */
    fun recognize(crop: Bitmap): OcrRead

    fun close()
}

/**
 * PaddleOCR PP-OCRv5 recognizer (§7C), Indian-plate fine-tuned, run on the TFLite/LiteRT runtime.
 * Preprocesses the crop to the model's fixed input (NCHW or NHWC auto-detected, normalised to [-1,1]),
 * runs the SVTR/CTC head, and greedy-CTC-decodes the output. Never fabricates a read — if not
 * initialised, [recognize] returns an empty [OcrRead].
 *
 * CONFIRM at build time: input H×W, channel order, the recognizer's character dictionary and the blank
 * index of your exported model, then set them on [CtcDecoder] / here.
 */
@Singleton
class PaddleOcrEngine @Inject constructor() : PlateOcrEngine {

    private var interpreter: Interpreter? = null
    private val decoder = CtcDecoder(CtcDecoder.PLATE_DICTIONARY, blankIndex = 0)

    override fun isReady(): Boolean = interpreter != null

    override fun init(modelFile: File) {
        require(modelFile.exists()) { "OCR model not provisioned: ${modelFile.name}" }
        close()
        val options = Interpreter.Options().apply {
            setUseXNNPACK(true)
            setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
        }
        interpreter = Interpreter(loadModel(modelFile), options)
        Timber.i("PaddleOcrEngine ready (${modelFile.name})")
    }

    override fun recognize(crop: Bitmap): OcrRead {
        val interp = interpreter ?: return OcrRead("", emptyList(), 0f)
        val inputTensor = interp.getInputTensor(0)
        val outputTensor = interp.getOutputTensor(0)

        val input = preprocess(crop, inputTensor)
        val outBuf = ByteBuffer.allocateDirect(outputTensor.numBytes()).order(ByteOrder.nativeOrder())
        interp.run(input, outBuf)

        val (timeSteps, numClasses) = ctcDims(outputTensor.shape())
        val logits = readFloats(outBuf, outputTensor)
        return decoder.decode(logits, timeSteps, numClasses)
    }

    /** Resize the crop to the model input and normalise to [-1,1] (PaddleOCR convention). */
    private fun preprocess(crop: Bitmap, tensor: Tensor): ByteBuffer {
        val shape = tensor.shape() // [1,3,H,W] (NCHW) or [1,H,W,3] (NHWC)
        val nchw = shape[1] == 3
        val h = if (nchw) shape[2] else shape[1]
        val w = if (nchw) shape[3] else shape[2]
        val scaled = Bitmap.createScaledBitmap(crop, w, h, true)
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled != crop) scaled.recycle()

        val buf = ByteBuffer.allocateDirect(tensor.numBytes()).order(ByteOrder.nativeOrder())
        val float = tensor.dataType() == DataType.FLOAT32
        fun norm(v: Int) = (v / 255f - 0.5f) / 0.5f

        if (nchw) {
            // channel-planar: all R, then all G, then all B.
            for (ch in 0 until 3) for (p in pixels) {
                val v = when (ch) { 0 -> (p shr 16) and 0xFF; 1 -> (p shr 8) and 0xFF; else -> p and 0xFF }
                putValue(buf, norm(v), float, tensor)
            }
        } else {
            for (p in pixels) {
                putValue(buf, norm((p shr 16) and 0xFF), float, tensor)
                putValue(buf, norm((p shr 8) and 0xFF), float, tensor)
                putValue(buf, norm(p and 0xFF), float, tensor)
            }
        }
        buf.rewind()
        return buf
    }

    private fun putValue(buf: ByteBuffer, value: Float, float: Boolean, tensor: Tensor) {
        if (float) {
            buf.putFloat(value)
        } else {
            val qp = tensor.quantizationParams()
            val q = Math.round(value / qp.scale) + qp.zeroPoint
            buf.put(q.coerceIn(-128, 255).toByte())
        }
    }

    private fun readFloats(buf: ByteBuffer, tensor: Tensor): FloatArray {
        buf.rewind()
        val n = tensor.shape().fold(1) { a, d -> a * d }
        val out = FloatArray(n)
        when (tensor.dataType()) {
            DataType.FLOAT32 -> buf.asFloatBuffer().get(out)
            DataType.UINT8 -> {
                val qp = tensor.quantizationParams()
                for (i in 0 until n) out[i] = qp.scale * ((buf.get(i).toInt() and 0xFF) - qp.zeroPoint)
            }
            DataType.INT8 -> {
                val qp = tensor.quantizationParams()
                for (i in 0 until n) out[i] = qp.scale * (buf.get(i).toInt() - qp.zeroPoint)
            }
            else -> error("Unsupported OCR output dtype ${tensor.dataType()}")
        }
        return out
    }

    /** Resolve (T, C) from the recognizer output shape, typically [1, T, C]. */
    private fun ctcDims(shape: IntArray): Pair<Int, Int> = when {
        shape.size == 3 -> shape[1] to shape[2]
        shape.size == 2 -> shape[0] to shape[1]
        else -> error("Unexpected OCR output shape ${shape.joinToString()}")
    }

    private fun loadModel(file: File): ByteBuffer = file.inputStream().use { fis ->
        fis.channel.map(FileChannel.MapMode.READ_ONLY, 0, fis.channel.size())
    }

    override fun close() {
        interpreter?.let { runCatching { it.close() } }
        interpreter = null
    }
}
