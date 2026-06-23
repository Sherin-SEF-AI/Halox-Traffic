package com.haloxtraffic.feature.anpr

import javax.inject.Inject
import javax.inject.Singleton

/** A correction attempt and whether it produced a valid plate. */
data class CorrectionResult(
    val original: String,
    val corrected: String,
    val validated: Boolean,
)

/**
 * Position-aware OCR confusion correction (§7D). Once the format fixes which positions are digits vs
 * letters, confusions are resolved deterministically (e.g. at a digit position `O→0`, `I→1`; at a
 * letter position the inverse). This single step yields large accuracy gains under motion blur.
 *
 * Pure and fully unit-testable.
 */
@Singleton
class PlateCorrector @Inject constructor(
    private val validator: PlateValidator,
) {
    /**
     * Try to correct [raw] into a valid plate by searching plausible state-series templates and
     * applying position-aware fixes. Returns the best (validated) candidate, or the canonicalised input
     * if nothing validates (never fabricates digits beyond confusion swaps).
     */
    fun correct(raw: String): CorrectionResult {
        val c = IndianPlate.canonicalize(raw)

        // Already valid as-is?
        validator.validate(c).let { if (it.valid) return CorrectionResult(c, c, true) }

        // BH series template: NN BH NNNN X(X).
        bhMask(c.length)?.let { mask ->
            val fixed = applyMask(c, mask)
            if (validator.validate(fixed).valid) return CorrectionResult(c, fixed, true)
        }

        // State-series: 2 letters + (1..2 digits) + (1..3 letters) + 4 digits.
        for (mask in standardMasks(c.length)) {
            val fixed = applyMask(c, mask)
            val v = validator.validate(fixed)
            if (v.valid) return CorrectionResult(c, fixed, true)
        }

        return CorrectionResult(c, c, false)
    }

    /** Apply a letter/digit [mask] ('A' = letter, '9' = digit) to [s], swapping OCR confusions. */
    fun applyMask(s: String, mask: String): String {
        if (s.length != mask.length) return s
        return buildString(s.length) {
            for (i in s.indices) {
                val ch = s[i]
                append(
                    when (mask[i]) {
                        '9' -> TO_DIGIT[ch] ?: ch
                        'A' -> TO_LETTER[ch] ?: ch
                        else -> ch
                    },
                )
            }
        }
    }

    private fun bhMask(len: Int): String? =
        if (len == 9 || len == 10) "99AA9999" + "A".repeat(len - 8) else null

    /** All plausible state-series masks for a given length (RTO 1–2 digits, series 1–3 letters). */
    private fun standardMasks(len: Int): List<String> {
        val middle = len - 6 // letters(2) + number(4) are fixed
        if (middle < 2 || middle > 5) return emptyList()
        val masks = ArrayList<String>()
        // Prefer a 2-digit RTO (by far the most common real layout) so ambiguous corrections resolve
        // to the likely plate rather than an also-valid but rarer 1-digit-RTO interpretation.
        for (rto in 2 downTo 1) {
            val series = middle - rto
            if (series in 1..3) {
                masks += "AA" + "9".repeat(rto) + "A".repeat(series) + "9999"
            }
        }
        return masks
    }

    companion object {
        // Map a character to its most likely DIGIT when the position must be numeric.
        val TO_DIGIT: Map<Char, Char> = mapOf(
            'O' to '0', 'D' to '0', 'Q' to '0',
            'I' to '1', 'L' to '1',
            'Z' to '2',
            'S' to '5',
            'B' to '8',
            'G' to '6',
            'T' to '7',
            'A' to '4',
        )

        // Inverse: map a character to its most likely LETTER when the position must be alphabetic.
        val TO_LETTER: Map<Char, Char> = mapOf(
            '0' to 'O',
            '1' to 'I',
            '2' to 'Z',
            '5' to 'S',
            '8' to 'B',
            '6' to 'G',
            '4' to 'A',
        )
    }
}
