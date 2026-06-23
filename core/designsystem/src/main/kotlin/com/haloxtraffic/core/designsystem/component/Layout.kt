package com.haloxtraffic.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.haloxtraffic.core.designsystem.theme.HaloxTheme
import com.haloxtraffic.core.designsystem.theme.SignalLevel

/** A 1px hairline. No shadows — separation is by line, not elevation (§11). */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier, strong: Boolean = false) {
    HorizontalDivider(
        modifier = modifier,
        thickness = HaloxTheme.dimens.hairline,
        color = if (strong) HaloxTheme.colors.hairlineStrong else HaloxTheme.colors.hairline,
    )
}

/**
 * A telemetry HUD row: a fixed-width grotesk label and a monospace value, with an optional trailing
 * status chip. Label width is fixed so values column-align and never jitter across rows.
 */
@Composable
fun TelemetryRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueEmphasis: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label.uppercase(),
            color = HaloxTheme.colors.inkFaint,
            style = HaloxTheme.typography.labelMicro,
            modifier = Modifier.widthIn(min = 64.dp),
        )
        MonoValue(value, emphasis = valueEmphasis, modifier = Modifier.widthIn(min = 0.dp))
        if (trailing != null) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { trailing() }
        }
    }
}

/** Top scrim over the camera feed so HUD text stays high-contrast regardless of the scene. */
@Composable
fun CameraScrim(modifier: Modifier = Modifier, fromTop: Boolean = true) {
    val colors = listOf(HaloxTheme.colors.void.copy(alpha = 0.78f), HaloxTheme.colors.void.copy(alpha = 0f))
    Box(
        modifier.background(
            Brush.verticalGradient(if (fromTop) colors else colors.reversed()),
        ),
    )
}

@Preview(backgroundColor = 0xFF0A0B0C, showBackground = true)
@Composable
private fun TelemetryPreview() {
    HaloxTheme {
        androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
            TelemetryRow("GPS", "8m", trailing = { StatusPill("LOCK", SignalLevel.CONFIRMED) })
            HairlineDivider()
            TelemetryRow("FPS", "12 / 13", valueEmphasis = true)
            TelemetryRow("THERM", "nominal")
        }
    }
}
