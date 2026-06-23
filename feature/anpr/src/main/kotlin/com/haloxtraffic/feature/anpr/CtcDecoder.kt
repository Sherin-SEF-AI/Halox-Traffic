package com.haloxtraffic.feature.anpr

import kotlin.math.exp

/**
 * Greedy CTC decoder for the PP-OCRv5 recognizer output (§7C). Takes a `[T, C]` logits grid (T
 * timesteps × C classes), applies softmax, picks the argmax per timestep, collapses repeats, drops the
 * blank, and maps indices to characters. Pure and unit-testable.
 *
 * PaddleOCR's CTCLabelDecode prepends a blank at index 0 (chars at 1..C-1 → dictionary[i-1]). Some
 * exports place blank last; [blankIndex] handles both.
 */
class CtcDecoder(
    private val dictionary: List<Char>,
    private val blankIndex: Int = 0,
) {
    /**
     * @param logits flat row-major `[T*C]`.
     * @param timeSteps T.
     * @param numClasses C.
     */
    fun decode(logits: FloatArray, timeSteps: Int, numClasses: Int): OcrRead {
        require(logits.size >= timeSteps * numClasses) { "logits too small for $timeSteps×$numClasses" }
        val chars = StringBuilder()
        val confidences = ArrayList<Float>()
        var prevIndex = -1

        for (t in 0 until timeSteps) {
            val base = t * numClasses
            var bestIdx = 0
            var bestVal = Float.NEGATIVE_INFINITY
            for (c in 0 until numClasses) {
                val v = logits[base + c]
                if (v > bestVal) { bestVal = v; bestIdx = c }
            }
            if (bestIdx != prevIndex && bestIdx != blankIndex) {
                charAt(bestIdx)?.let { ch ->
                    chars.append(ch)
                    confidences += softmaxProb(logits, base, numClasses, bestIdx)
                }
            }
            prevIndex = bestIdx
        }

        val text = chars.toString()
        val overall = if (confidences.isEmpty()) 0f else confidences.average().toFloat()
        return OcrRead(text = text, perCharConfidence = confidences, overall = overall)
    }

    private fun charAt(index: Int): Char? = when {
        index == blankIndex -> null
        blankIndex == 0 -> dictionary.getOrNull(index - 1)
        else -> dictionary.getOrNull(index)
    }

    /** Stable softmax probability of class [idx] within one timestep row. */
    private fun softmaxProb(logits: FloatArray, base: Int, n: Int, idx: Int): Float {
        var max = Float.NEGATIVE_INFINITY
        for (c in 0 until n) if (logits[base + c] > max) max = logits[base + c]
        var sum = 0.0
        for (c in 0 until n) sum += exp((logits[base + c] - max).toDouble())
        return (exp((logits[base + idx] - max).toDouble()) / sum).toFloat()
    }

    companion object {
        /** Default Indian-plate charset: digits + uppercase letters. */
        val PLATE_DICTIONARY: List<Char> = ('0'..'9') + ('A'..'Z')
    }
}
