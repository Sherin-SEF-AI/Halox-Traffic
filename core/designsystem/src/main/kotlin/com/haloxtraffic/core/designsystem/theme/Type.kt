package com.haloxtraffic.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Operational Materialism typography (§11): monospace for ALL data (plate strings, coordinates,
 * timestamps, confidences, latency), a neutral grotesk/sans for labels and prose. Data styles use
 * tabular-aligned monospace so numbers do not jitter as values change.
 *
 * NOTE: ships with platform [FontFamily.Monospace] / [FontFamily.SansSerif] so the module is
 * self-contained. Swap in bundled fonts (e.g. JetBrains Mono / Inter) by replacing the families here.
 */
@Immutable
data class HaloxTypography(
    /** Large monospace readout — the live plate string. */
    val dataDisplay: TextStyle,
    /** Standard monospace value — coords, timestamps, latency. */
    val dataValue: TextStyle,
    /** Small monospace value — dense telemetry rows. */
    val dataSmall: TextStyle,
    /** Section / field label. */
    val label: TextStyle,
    /** Small caps-style label for chips. */
    val labelMicro: TextStyle,
    /** Body prose. */
    val body: TextStyle,
    /** Screen title. */
    val title: TextStyle,
)

private val Mono = FontFamily.Monospace
private val Sans = FontFamily.SansSerif

val HaloxTypographyTokens = HaloxTypography(
    dataDisplay = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = 1.sp,
    ),
    dataValue = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 18.sp, letterSpacing = 0.5.sp,
    ),
    dataSmall = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 15.sp, letterSpacing = 0.3.sp,
    ),
    label = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp,
    ),
    labelMicro = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 1.2.sp,
        textAlign = TextAlign.Start,
    ),
    body = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp,
    ),
    title = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp,
    ),
)

/** Material3 [Typography] mapped from our tokens so stock components inherit the system. */
internal val Material3Typography = Typography(
    displaySmall = HaloxTypographyTokens.dataDisplay,
    titleLarge = HaloxTypographyTokens.title,
    titleMedium = HaloxTypographyTokens.title.copy(fontSize = 16.sp),
    bodyLarge = HaloxTypographyTokens.body,
    bodyMedium = HaloxTypographyTokens.body.copy(fontSize = 14.sp),
    labelLarge = HaloxTypographyTokens.label,
    labelMedium = HaloxTypographyTokens.label.copy(fontSize = 12.sp),
    labelSmall = HaloxTypographyTokens.labelMicro,
)

val LocalHaloxTypography = staticCompositionLocalOf { HaloxTypographyTokens }
