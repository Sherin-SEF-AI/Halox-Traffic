package com.haloxtraffic.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Operational Materialism palette (§11). Matte graphite / near-black surfaces, hairline dividers, no
 * gloss. Colour is *earned*: the canvas is monochrome and hue only ever carries operational meaning.
 */
internal object HaloxPalette {
    // Matte canvas — flat planes, increasing in lightness with elevation but never glossy.
    val Void = Color(0xFF0A0B0C)
    val Graphite = Color(0xFF121417)
    val GraphiteRaised = Color(0xFF1A1D21)
    val GraphiteHigh = Color(0xFF22262B)

    // Hairlines.
    val Hairline = Color(0xFF2A2E33)
    val HairlineStrong = Color(0xFF3A3F46)

    // Text.
    val Ink = Color(0xFFE6E8EA)
    val InkMuted = Color(0xFF9AA0A6)
    val InkFaint = Color(0xFF5A6066)

    // Operational semantics — the only saturated colours in the system.
    /** Confirmed / validated. */
    val SignalGreen = Color(0xFF2ECC71)
    /** Active violation. */
    val SignalRed = Color(0xFFFF3B30)
    /** Degraded / uncertain plate / weak GPS / thermal throttle / untrusted time. */
    val SignalAmber = Color(0xFFFFB020)

    // Muted backings for signal chips (10–16% over canvas), so colour reads without glowing.
    val SignalGreenDim = Color(0xFF14301F)
    val SignalRedDim = Color(0xFF3A1512)
    val SignalAmberDim = Color(0xFF332408)

    val OnSignal = Color(0xFF0A0B0C)
}
