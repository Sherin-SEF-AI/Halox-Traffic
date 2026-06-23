package com.haloxtraffic.feature.reports

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.haloxtraffic.core.designsystem.component.EmptyState

/**
 * Reports + analytics (§12.6): case-file PDFs, e-challan bundle export, CSV, and analytics
 * (by type/location/time, plate-read accuracy, hotspots, repeat plates). Lands in Phase 8.
 */
@Composable
fun ReportsScreen(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Filled.Assessment,
        title = "Reports & analytics",
        message = "Exports and analytics become available once cases exist (Phase 8).",
        modifier = modifier.fillMaxSize(),
    )
}
