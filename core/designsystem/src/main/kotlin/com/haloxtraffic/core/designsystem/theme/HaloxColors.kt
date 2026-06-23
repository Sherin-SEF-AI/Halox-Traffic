package com.haloxtraffic.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Extended operational colour roles not expressible in a stock Material [androidx.compose.material3.ColorScheme].
 * Provided via [LocalHaloxColors]; access through `HaloxTheme.colors`.
 */
@Immutable
data class HaloxColors(
    val void: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceHigh: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val confirmed: Color,
    val confirmedDim: Color,
    val violation: Color,
    val violationDim: Color,
    val degraded: Color,
    val degradedDim: Color,
    val onSignal: Color,
) {
    /** The earned-colour for an operational [SignalLevel]. */
    fun signal(level: SignalLevel): Color = when (level) {
        SignalLevel.CONFIRMED -> confirmed
        SignalLevel.VIOLATION -> violation
        SignalLevel.DEGRADED -> degraded
        SignalLevel.NEUTRAL -> inkMuted
    }

    fun signalDim(level: SignalLevel): Color = when (level) {
        SignalLevel.CONFIRMED -> confirmedDim
        SignalLevel.VIOLATION -> violationDim
        SignalLevel.DEGRADED -> degradedDim
        SignalLevel.NEUTRAL -> surfaceRaised
    }
}

/**
 * Operational state for colour selection. A quiet road is [NEUTRAL] — colourless on purpose. Never
 * use colour alone: every chip pairs the colour with an icon/label so it survives colour-blindness.
 */
enum class SignalLevel { NEUTRAL, CONFIRMED, VIOLATION, DEGRADED }

internal val DarkHaloxColors = HaloxColors(
    void = HaloxPalette.Void,
    surface = HaloxPalette.Graphite,
    surfaceRaised = HaloxPalette.GraphiteRaised,
    surfaceHigh = HaloxPalette.GraphiteHigh,
    hairline = HaloxPalette.Hairline,
    hairlineStrong = HaloxPalette.HairlineStrong,
    ink = HaloxPalette.Ink,
    inkMuted = HaloxPalette.InkMuted,
    inkFaint = HaloxPalette.InkFaint,
    confirmed = HaloxPalette.SignalGreen,
    confirmedDim = HaloxPalette.SignalGreenDim,
    violation = HaloxPalette.SignalRed,
    violationDim = HaloxPalette.SignalRedDim,
    degraded = HaloxPalette.SignalAmber,
    degradedDim = HaloxPalette.SignalAmberDim,
    onSignal = HaloxPalette.OnSignal,
)

val LocalHaloxColors = staticCompositionLocalOf { DarkHaloxColors }
