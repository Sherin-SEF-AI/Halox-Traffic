package com.haloxtraffic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.haloxtraffic.core.designsystem.component.HaloxCard
import com.haloxtraffic.core.designsystem.theme.HaloxTheme
import com.haloxtraffic.navigation.Routes

/** Hub to every section. Live Enforcement is the primary action; the rest are review/admin surfaces. */
@Composable
fun HomeScreen(onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("HaloxTraffic", style = HaloxTheme.typography.title, color = HaloxTheme.colors.ink)
        Text(
            "On-device violation detection · ANPR · sealed evidence",
            style = HaloxTheme.typography.body,
            color = HaloxTheme.colors.inkMuted,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        HomeTile("Live Enforcement", "Detect violations, read plates, seal evidence", Icons.Filled.Videocam, primary = true) {
            onNavigate(Routes.LIVE)
        }
        HomeTile("Case Files", "Review, confirm, dismiss, correct plates", Icons.AutoMirrored.Filled.Assignment) {
            onNavigate(Routes.CASES)
        }
        HomeTile("Junction Setup", "Mark stop-line / signal ROI / lanes on camera", Icons.Filled.Tune) {
            onNavigate(Routes.JUNCTION_CONFIG)
        }
        HomeTile("Map", "Violation map + heatmap", Icons.Filled.Map) {
            onNavigate(Routes.MAP)
        }
        HomeTile("Reports", "Exports + analytics", Icons.Filled.Assessment) {
            onNavigate(Routes.REPORTS)
        }
        HomeTile("Sync & Evidence", "Queue, integrity self-check, Vahan", Icons.Filled.CloudSync) {
            onNavigate(Routes.SYNC)
        }
        HomeTile("Settings", "Tier, thresholds, retention, privacy", Icons.Filled.Settings) {
            onNavigate(Routes.SETTINGS)
        }
    }
}

@Composable
private fun HomeTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    HaloxCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (primary) HaloxTheme.colors.confirmed else HaloxTheme.colors.ink,
            )
            Column {
                Text(title, style = HaloxTheme.typography.label, color = HaloxTheme.colors.ink)
                Text(subtitle, style = HaloxTheme.typography.body, color = HaloxTheme.colors.inkMuted)
            }
        }
    }
}
