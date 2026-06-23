package com.haloxtraffic.feature.anpr

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CtcDecoderTest {

    private val dict = CtcDecoder.PLATE_DICTIONARY // ['0'..'9','A'..'Z']
    private val decoder = CtcDecoder(dict, blankIndex = 0)
    private val numClasses = dict.size + 1 // + blank

    /** Class index for a character under blank-at-0 convention. */
    private fun classOf(ch: Char): Int = dict.indexOf(ch) + 1

    /** Build a [T*C] logits grid where each timestep's argmax is the given class index. */
    private fun logitsFor(sequence: List<Int>): FloatArray {
        val t = sequence.size
        val arr = FloatArray(t * numClasses)
        sequence.forEachIndexed { i, cls -> arr[i * numClasses + cls] = 10f }
        return arr
    }

    @Test fun `greedy decode collapses repeats and drops blanks`() {
        // K, K, blank, A, 0, 0, blank, 1  →  "KA01"
        val seq = listOf(classOf('K'), classOf('K'), 0, classOf('A'), classOf('0'), classOf('0'), 0, classOf('1'))
        val read = decoder.decode(logitsFor(seq), timeSteps = seq.size, numClasses = numClasses)
        assertThat(read.text).isEqualTo("KA01")
        assertThat(read.perCharConfidence).hasSize(4)
        assertThat(read.overall).isGreaterThan(0.9f)
    }

    @Test fun `all-blank output yields empty read`() {
        val read = decoder.decode(logitsFor(listOf(0, 0, 0)), timeSteps = 3, numClasses = numClasses)
        assertThat(read.text).isEmpty()
        assertThat(read.overall).isEqualTo(0f)
    }

    @Test fun `blank-last convention maps indices without offset`() {
        val blankLast = CtcDecoder(dict, blankIndex = dict.size)
        // dict index for '5' is 5 → class 5 (no +1 offset when blank is last).
        val seq = listOf(dict.indexOf('5'), dict.size /*blank*/, dict.indexOf('5'))
        val read = blankLast.decode(logitsFor(seq), timeSteps = seq.size, numClasses = numClasses)
        assertThat(read.text).isEqualTo("55")
    }
}
