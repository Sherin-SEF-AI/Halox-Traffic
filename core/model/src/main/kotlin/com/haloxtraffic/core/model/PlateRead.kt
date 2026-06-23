package com.haloxtraffic.core.model

import kotlinx.serialization.Serializable

/**
 * Result of the ANPR pipeline for one vehicle (§7). The plate string always carries a confidence and
 * a [validated] flag; an unvalidatable read is stored as a candidate marked for human confirmation,
 * never as a clean result. A genuinely unreadable plate yields [plate] == null with [uncertain] true.
 */
@Serializable
data class PlateRead(
    /** Consensus plate string, canonicalised (no spaces), or null if unreadable. */
    val plate: String?,
    /** Overall confidence 0f..1f. */
    val confidence: Float,
    /** Per-character confidence, aligned to [plate]. */
    val perCharConfidence: List<Float>,
    val format: PlateFormat?,
    val color: PlateColor,
    /** True only when format + state-code + regex validation all passed. */
    val validated: Boolean,
    /** True when the read is low-confidence/ambiguous and needs human or VLM confirmation. */
    val uncertain: Boolean,
    /** Number of frames fused into this consensus. */
    val framesFused: Int,
) {
    companion object {
        /** An explicit unreadable result — never fabricate a plate. */
        fun unreadable(): PlateRead = PlateRead(
            plate = null,
            confidence = 0f,
            perCharConfidence = emptyList(),
            format = null,
            color = PlateColor.UNKNOWN,
            validated = false,
            uncertain = true,
            framesFused = 0,
        )
    }
}
