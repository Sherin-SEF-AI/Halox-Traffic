package com.haloxtraffic.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.haloxtraffic.core.designsystem.theme.HaloxTheme
import com.haloxtraffic.core.designsystem.theme.SignalLevel

/**
 * Dev-only gallery of the Operational Materialism component set, so the visual language stays
 * consistent and is demoable. Not wired into the production nav graph.
 */
@Composable
fun ComponentGallery(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("HaloxTraffic · Operational Materialism", style = HaloxTheme.typography.title, color = HaloxTheme.colors.ink)

        Text("DATA", style = HaloxTheme.typography.labelMicro, color = HaloxTheme.colors.inkFaint)
        PlateReadout("KA05MH2453")
        MonoValue("12.9716, 77.5946", emphasis = true)

        Text("SIGNALS", style = HaloxTheme.typography.labelMicro, color = HaloxTheme.colors.inkFaint)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ViolationBadge("No Helmet")
            ConfidenceTag(0.94f, true)
            ConfidenceTag(0.51f, false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill("SEALED", SignalLevel.CONFIRMED)
            StatusPill("GPS WEAK", SignalLevel.DEGRADED)
            StatusPill("IDLE", SignalLevel.NEUTRAL)
        }

        Text("TELEMETRY", style = HaloxTheme.typography.labelMicro, color = HaloxTheme.colors.inkFaint)
        TelemetryRow("FPS", "12 / 13", valueEmphasis = true)
        HairlineDivider()
        TelemetryRow("GPS", "8m", trailing = { StatusPill("LOCK", SignalLevel.CONFIRMED) })

        Text("ACTIONS", style = HaloxTheme.typography.labelMicro, color = HaloxTheme.colors.inkFaint)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OperationalButton("Capture", {}, kind = ButtonKind.PRIMARY)
            OperationalButton("Pause", {})
            OperationalButton("End", {}, kind = ButtonKind.DANGER)
        }

        Text("CARDS", style = HaloxTheme.typography.labelMicro, color = HaloxTheme.colors.inkFaint)
        TierResultCard("MID", listOf("ram=6144MB", "soc=Dimensity 900", "gpu=yes nnapi=yes", "vlm=disabled"))
        ProvisioningProgress("ppocrv5-small.tflite", 0.4f, "downloading")

        EmptyState(Icons.Filled.Inbox, "No cases yet", "Committed violations will appear here.")
    }
}

@Preview(backgroundColor = 0xFF0A0B0C, showBackground = true, heightDp = 1400)
@Composable
private fun GalleryPreview() {
    HaloxTheme { ComponentGallery() }
}
