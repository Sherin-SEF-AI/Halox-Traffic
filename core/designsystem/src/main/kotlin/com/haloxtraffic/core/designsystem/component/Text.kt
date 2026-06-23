package com.haloxtraffic.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.haloxtraffic.core.designsystem.theme.HaloxTheme

/**
 * A monospace data readout with tabular figures so the layout never shifts as the value changes —
 * the backbone of the telemetry HUD. Use for any *value* (plate, coords, latency), never for labels.
 */
@Composable
fun MonoValue(
    text: String,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier,
        color = if (emphasis) HaloxTheme.colors.ink else HaloxTheme.colors.inkMuted,
        style = if (emphasis) HaloxTheme.typography.dataValue else HaloxTheme.typography.dataSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The large plate readout for the live HUD and case file. */
@Composable
fun PlateReadout(plate: String, modifier: Modifier = Modifier) {
    Text(
        text = plate,
        modifier = modifier,
        color = HaloxTheme.colors.ink,
        style = HaloxTheme.typography.dataDisplay,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0B0C)
@Composable
private fun MonoValuePreview() {
    HaloxTheme {
        Column(Modifier.padding(16.dp)) {
            PlateReadout("KA05MH2453")
            MonoValue("12.9716, 77.5946", emphasis = true)
            MonoValue("gps=8m  fps=12  lat=41ms")
        }
    }
}
