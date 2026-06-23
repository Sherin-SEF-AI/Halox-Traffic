package com.haloxtraffic.feature.detection.runtime

import com.haloxtraffic.core.model.BoundingBox
import com.haloxtraffic.feature.detection.model.BoxFormat
import com.haloxtraffic.feature.detection.model.DetectorOutputLayout

/**
 * Decodes the raw YOLO26 output tensor into [BoundingBox]es. YOLO26 is **NMS-free** — the model emits
 * final, deduplicated boxes, so this decoder parses them directly and runs NO NMS pass (§3/§4).
 *
 * Pure and unit-testable: feed a flat [FloatArray] in the documented layout. CONFIRM the layout
 * (attribute order, box format, normalization) against your own export and update [DetectorOutputLayout].
 */
object Yolo26Decoder {

    /**
     * @param output flat output tensor, length == numDetections * attributesPerBox.
     * @param layout the export's confirmed output layout.
     * @param scoreThreshold drop boxes below this confidence.
     */
    fun decode(
        output: FloatArray,
        layout: DetectorOutputLayout,
        scoreThreshold: Float = 0.25f,
    ): List<BoundingBox> {
        val stride = layout.attributesPerBox
        require(stride >= 6) { "Expected at least [x,y,w,h,score,classId] per box" }
        val count = minOf(layout.numDetections, output.size / stride)
        val result = ArrayList<BoundingBox>(count)

        for (i in 0 until count) {
            val o = i * stride
            val score = output[o + 4]
            if (score < scoreThreshold) continue
            val classId = output[o + 5].toInt()

            val (l, t, r, b) = when (layout.boxFormat) {
                BoxFormat.XYWH -> {
                    val cx = output[o]; val cy = output[o + 1]
                    val w = output[o + 2]; val h = output[o + 3]
                    Quad(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
                }
                BoxFormat.XYXY -> Quad(output[o], output[o + 1], output[o + 2], output[o + 3])
            }

            // Normalize if the export emits pixel coords against the square input.
            val norm = if (layout.coordsNormalized) Quad(l, t, r, b)
            else Quad(l / DEFAULT_INPUT, t / DEFAULT_INPUT, r / DEFAULT_INPUT, b / DEFAULT_INPUT)

            result += BoundingBox(
                left = norm.a.coerceIn(0f, 1f),
                top = norm.b.coerceIn(0f, 1f),
                right = norm.c.coerceIn(0f, 1f),
                bottom = norm.d.coerceIn(0f, 1f),
                score = score,
                classId = classId,
            )
        }
        return result
    }

    private const val DEFAULT_INPUT = 640f
    private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)
    private operator fun Quad.component1() = a
    private operator fun Quad.component2() = b
    private operator fun Quad.component3() = c
    private operator fun Quad.component4() = d
}
