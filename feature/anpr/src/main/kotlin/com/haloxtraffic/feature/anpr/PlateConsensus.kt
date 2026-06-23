package com.haloxtraffic.feature.anpr

import javax.inject.Inject
import javax.inject.Singleton

/** One raw OCR read of a plate crop. */
data class OcrRead(
    val text: String,
    /** Per-character confidence aligned to [text]; may be empty (treated as uniform). */
    val perCharConfidence: List<Float>,
    val overall: Float,
)

/** Consensus across multiple frames' reads. */
data class ConsensusResult(
    val text: String,
    val perCharConfidence: List<Float>,
    val overall: Float,
    val framesFused: Int,
)

/**
 * Multi-frame consensus (§7D): vote per character position, confidence-weighted, across the N reads.
 * Reads are grouped by canonical length and the modal length wins; positions then take the
 * highest-weighted character. Pure and unit-testable.
 */
@Singleton
class PlateConsensus @Inject constructor() {

    fun fuse(reads: List<OcrRead>): ConsensusResult? {
        val canon = reads
            .map { it.copy(text = IndianPlate.canonicalize(it.text)) }
            .filter { it.text.isNotEmpty() }
        if (canon.isEmpty()) return null

        // Pick the modal length (ties → longer), then vote among reads of that length.
        val modalLength = canon.groupingBy { it.text.length }.eachCount()
            .entries.maxWith(compareBy({ it.value }, { it.key }))
            .key
        val pool = canon.filter { it.text.length == modalLength }

        val resultChars = CharArray(modalLength)
        val resultConf = FloatArray(modalLength)
        for (pos in 0 until modalLength) {
            // Confidence-weighted vote for this position.
            val weights = HashMap<Char, Float>()
            for (read in pool) {
                val ch = read.text[pos]
                val w = read.perCharConfidence.getOrElse(pos) { read.overall.coerceAtLeast(0.01f) }
                weights[ch] = (weights[ch] ?: 0f) + w
            }
            val (bestChar, bestWeight) = weights.entries.maxBy { it.value }.toPair()
            resultChars[pos] = bestChar
            // Absolute confidence: summed weight of the winning char over the number of reads. This
            // preserves the underlying OCR confidence (a single low-confidence read stays low) while
            // disagreement among reads lowers the share that backs the winner.
            resultConf[pos] = (bestWeight / pool.size).coerceIn(0f, 1f)
        }

        val text = String(resultChars)
        val overall = resultConf.average().toFloat()
        return ConsensusResult(text, resultConf.toList(), overall, framesFused = pool.size)
    }

    private fun <K, V> Map.Entry<K, V>.toPair() = key to value
}
