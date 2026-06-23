package com.haloxtraffic.core.designsystem.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Operational Materialism motion (§11): binary and discrete. States *snap* — detecting/committed,
 * valid/uncertain. No decorative easing. The only animation permitted is a short cross-fade for
 * value changes so a flicker is legible, and an instant snap for state changes.
 */
@Immutable
data class HaloxMotion(
    /** State transitions are instantaneous — they snap, not ease. */
    val stateSnap: AnimationSpec<Float> = snap(),
    /** A minimal, non-decorative fade for value readouts (keeps numbers legible on change). */
    val valueFade: AnimationSpec<Float> = tween(durationMillis = 90),
)

val LocalHaloxMotion = staticCompositionLocalOf { HaloxMotion() }
