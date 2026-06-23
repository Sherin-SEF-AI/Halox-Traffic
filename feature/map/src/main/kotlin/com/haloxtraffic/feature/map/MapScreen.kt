package com.haloxtraffic.feature.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.haloxtraffic.core.designsystem.component.EmptyState

/**
 * Violation map + heatmap (§12.5) and junction-geometry config (§12.3). Phase 6/8 brings MapLibre GL
 * (offline-tile capable) online here. Placeholder until then.
 */
@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Filled.Map,
        title = "Map coming online",
        message = "MapLibre violation map, heatmap and junction geometry land in Phase 6/8.",
        modifier = modifier.fillMaxSize(),
    )
}
