package com.haloxtraffic.feature.detection.runtime

import android.graphics.Bitmap
import com.haloxtraffic.core.model.BoundingBox
import com.haloxtraffic.core.model.DetectionConfig
import com.haloxtraffic.core.model.InferenceDelegate
import com.haloxtraffic.feature.detection.model.ModelSpec
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import timber.log.Timber
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureNanoTime

/**
 * LiteRT-backed YOLO26 detector (§4). Loads the provisioned `.tflite` with a GPU → NNAPI → XNNPACK/CPU
 * delegate fallback (never hard-failing on a delegate init error), runs inference, and decodes the
 * NMS-free output via [Yolo26Decoder]. Handles FLOAT32 and quantised (UINT8/INT8) I/O by reading the
 * model's own tensor dtype + quant params — so the same code serves every tier.
 *
 * It never fabricates detections: if not initialised, [detect] throws and the controller treats the
 * detector as not-ready.
 *
 * NOTE: LiteRT (Google AI Edge) keeps the `org.tensorflow.lite.*` class packages. Confirm the exported
 * output tensor shape matches [com.haloxtraffic.feature.detection.model.DetectorOutputLayout].
 */
@Singleton
class LiteRtDetector @Inject constructor() : Detector {

    private var interpreter: Interpreter? = null
    private var spec: ModelSpec? = null
    private var gpuDelegate: GpuDelegate? = null
    private var nnApiDelegate: NnApiDelegate? = null
    private var inputSize: Int = 0

    override var activeDelegate: InferenceDelegate? = null
        private set

    override fun isReady(): Boolean = interpreter != null

    override fun init(modelFile: File, spec: ModelSpec, config: DetectionConfig): InferenceDelegate {
        require(modelFile.exists()) { "Model not provisioned: ${modelFile.name}" }
        close()
        this.spec = spec
        this.inputSize = spec.inputSize
        val model = loadModel(modelFile)

        // Try each delegate in precedence order; first that builds an Interpreter wins.
        for (delegate in DelegateSelector.chain(config)) {
            val attempt = runCatching { buildInterpreter(model, delegate, config) }
            if (attempt.isSuccess) {
                interpreter = attempt.getOrThrow()
                activeDelegate = delegate
                Timber.i("LiteRtDetector ready: ${spec.variant} on $delegate (in=${spec.inputSize})")
                return delegate
            }
            Timber.w(attempt.exceptionOrNull(), "Delegate $delegate init failed; trying next")
            releaseDelegates()
        }
        error("No delegate could initialise the interpreter")
    }

    private fun buildInterpreter(model: ByteBuffer, delegate: InferenceDelegate, config: DetectionConfig): Interpreter {
        val options = Interpreter.Options()
        when (delegate) {
            InferenceDelegate.GPU -> gpuDelegate = GpuDelegate().also { options.addDelegate(it) }
            InferenceDelegate.NNAPI -> nnApiDelegate = NnApiDelegate().also { options.addDelegate(it) }
            InferenceDelegate.XNNPACK_CPU -> {
                options.setUseXNNPACK(true)
                options.setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
            }
        }
        return Interpreter(model, options)
    }

    override fun detect(input: Bitmap, scoreThreshold: Float): DetectionResult {
        val interp = interpreter ?: error("Detector not initialised")
        val layout = spec?.detectorOutput ?: error("No output layout for ${spec?.variant}")
        val inputTensor = interp.getInputTensor(0)
        val outputTensor = interp.getOutputTensor(0)

        val inputBuffer = fillInput(input, inputTensor)
        val outputBuffer = ByteBuffer.allocateDirect(outputTensor.numBytes()).order(ByteOrder.nativeOrder())

        val ns = measureNanoTime { interp.run(inputBuffer, outputBuffer) }
        val floats = readOutput(outputBuffer, outputTensor)
        val boxes: List<BoundingBox> = Yolo26Decoder.decode(floats, layout, scoreThreshold)

        return DetectionResult(boxes, activeDelegate ?: InferenceDelegate.XNNPACK_CPU, ns / 1_000_000)
    }

    /** Pack the square bitmap into the model's input dtype (FLOAT32 [0,1] or quantised UINT8/INT8). */
    private fun fillInput(bitmap: Bitmap, tensor: Tensor): ByteBuffer {
        val size = inputSize
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        val buffer = ByteBuffer.allocateDirect(tensor.numBytes()).order(ByteOrder.nativeOrder())

        when (tensor.dataType()) {
            DataType.FLOAT32 -> {
                for (p in pixels) {
                    buffer.putFloat(((p shr 16) and 0xFF) / 255f)
                    buffer.putFloat(((p shr 8) and 0xFF) / 255f)
                    buffer.putFloat((p and 0xFF) / 255f)
                }
            }
            DataType.UINT8, DataType.INT8 -> {
                val qp = tensor.quantizationParams()
                val signed = tensor.dataType() == DataType.INT8
                for (p in pixels) {
                    buffer.put(quantize(((p shr 16) and 0xFF) / 255f, qp.scale, qp.zeroPoint, signed))
                    buffer.put(quantize(((p shr 8) and 0xFF) / 255f, qp.scale, qp.zeroPoint, signed))
                    buffer.put(quantize((p and 0xFF) / 255f, qp.scale, qp.zeroPoint, signed))
                }
            }
            else -> error("Unsupported input dtype ${tensor.dataType()}")
        }
        buffer.rewind()
        return buffer
    }

    /** Read the output tensor into a flat FloatArray, dequantising quantised outputs. */
    private fun readOutput(buffer: ByteBuffer, tensor: Tensor): FloatArray {
        buffer.rewind()
        val n = tensor.shape().fold(1) { acc, d -> acc * d }
        val out = FloatArray(n)
        when (tensor.dataType()) {
            DataType.FLOAT32 -> buffer.asFloatBuffer().get(out)
            DataType.UINT8 -> {
                val qp = tensor.quantizationParams()
                for (i in 0 until n) out[i] = qp.scale * ((buffer.get(i).toInt() and 0xFF) - qp.zeroPoint)
            }
            DataType.INT8 -> {
                val qp = tensor.quantizationParams()
                for (i in 0 until n) out[i] = qp.scale * (buffer.get(i).toInt() - qp.zeroPoint)
            }
            else -> error("Unsupported output dtype ${tensor.dataType()}")
        }
        return out
    }

    private fun quantize(value: Float, scale: Float, zeroPoint: Int, signed: Boolean): Byte {
        val q = Math.round(value / scale) + zeroPoint
        return if (signed) q.coerceIn(-128, 127).toByte() else q.coerceIn(0, 255).toByte()
    }

    private fun loadModel(file: File): ByteBuffer = file.inputStream().use { fis ->
        // The memory-mapped buffer remains valid after the stream/channel are closed.
        fis.channel.map(FileChannel.MapMode.READ_ONLY, 0, fis.channel.size())
    }

    private fun releaseDelegates() {
        (gpuDelegate as? Closeable)?.let { runCatching { it.close() } }
        nnApiDelegate?.let { runCatching { it.close() } }
        gpuDelegate = null
        nnApiDelegate = null
    }

    override fun close() {
        interpreter?.let { runCatching { it.close() } }
        interpreter = null
        releaseDelegates()
        activeDelegate = null
        spec = null
    }
}
