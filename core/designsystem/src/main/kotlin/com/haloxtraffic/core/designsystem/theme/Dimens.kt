package com.haloxtraffic.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing + sizing tokens. The live screen is dense (telemetry console), forms are roomier. */
@Immutable
data class HaloxDimens(
    val hairline: Dp = 1.dp,
    val spaceXxs: Dp = 2.dp,
    val spaceXs: Dp = 4.dp,
    val spaceSm: Dp = 8.dp,
    val spaceMd: Dp = 12.dp,
    val spaceLg: Dp = 16.dp,
    val spaceXl: Dp = 24.dp,
    val spaceXxl: Dp = 32.dp,
    /** Flat planes use small radii — no pill softness. */
    val radiusSm: Dp = 4.dp,
    val radiusMd: Dp = 8.dp,
    /** Minimum interactive target for accessibility (§11/§14). */
    val touchTarget: Dp = 48.dp,
    val actionBandHeight: Dp = 88.dp,
)

val LocalHaloxDimens = staticCompositionLocalOf { HaloxDimens() }
