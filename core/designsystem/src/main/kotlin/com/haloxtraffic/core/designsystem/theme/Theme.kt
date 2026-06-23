package com.haloxtraffic.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * HaloxTheme — Operational Materialism on a Material3 substrate, fully restyled. The app is
 * always dark/matte (an enforcement instrument); [isSystemInDarkTheme] is intentionally ignored.
 *
 * Access extended tokens through [HaloxTheme] (`HaloxTheme.colors`, `.typography`, `.dimens`, `.motion`).
 */
@Composable
fun HaloxTheme(content: @Composable () -> Unit) {
    val colors = DarkHaloxColors
    val material = darkColorScheme(
        background = colors.void,
        onBackground = colors.ink,
        surface = colors.surface,
        onSurface = colors.ink,
        surfaceVariant = colors.surfaceRaised,
        onSurfaceVariant = colors.inkMuted,
        outline = colors.hairline,
        outlineVariant = colors.hairline,
        primary = colors.confirmed,
        onPrimary = colors.onSignal,
        error = colors.violation,
        onError = colors.onSignal,
        tertiary = colors.degraded,
    )

    CompositionLocalProvider(
        LocalHaloxColors provides colors,
        LocalHaloxTypography provides HaloxTypographyTokens,
        LocalHaloxDimens provides HaloxDimens(),
        LocalHaloxMotion provides HaloxMotion(),
    ) {
        MaterialTheme(
            colorScheme = material,
            typography = Material3Typography,
            content = content,
        )
    }
}

/** Token accessor object, mirroring the `MaterialTheme` convention. */
object HaloxTheme {
    val colors: HaloxColors
        @Composable @ReadOnlyComposable get() = LocalHaloxColors.current
    val typography: HaloxTypography
        @Composable @ReadOnlyComposable get() = LocalHaloxTypography.current
    val dimens: HaloxDimens
        @Composable @ReadOnlyComposable get() = LocalHaloxDimens.current
    val motion: HaloxMotion
        @Composable @ReadOnlyComposable get() = LocalHaloxMotion.current
}
