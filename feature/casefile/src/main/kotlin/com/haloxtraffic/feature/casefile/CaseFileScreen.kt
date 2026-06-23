package com.haloxtraffic.feature.casefile

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.haloxtraffic.core.designsystem.component.EmptyState

/**
 * Case review (§12.4). Phase 5 fills this with the case list + detail (clip, stills, plate crop,
 * FSM trace, geo, confirm/dismiss/correct-plate). Placeholder until evidence sealing lands.
 */
@Composable
fun CaseFileScreen(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Filled.Gavel,
        title = "No cases yet",
        message = "Committed violations and their sealed evidence will appear here (Phase 5).",
        modifier = modifier.fillMaxSize(),
    )
}
