package com.haloxtraffic.feature.casefile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.haloxtraffic.core.data.entity.ViolationCaseEntity
import com.haloxtraffic.core.designsystem.component.ConfidenceTag
import com.haloxtraffic.core.designsystem.component.EmptyState
import com.haloxtraffic.core.designsystem.component.HaloxCard
import com.haloxtraffic.core.designsystem.component.MonoValue
import com.haloxtraffic.core.designsystem.component.StatusPill
import com.haloxtraffic.core.designsystem.theme.HaloxTheme
import com.haloxtraffic.core.designsystem.theme.SignalLevel
import com.haloxtraffic.core.model.CaseStatus
import com.haloxtraffic.core.model.ViolationType

/** Case list (§12.4). Tap a case to open its sealed detail + review actions. */
@Composable
fun CaseFileScreen(
    onOpenCase: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CaseFileViewModel = hiltViewModel(),
) {
    val cases by viewModel.cases.collectAsStateWithLifecycle()

    if (cases.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Gavel,
            title = "No cases yet",
            message = "Committed violations are sealed here with tamper-evident evidence.",
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Cases", style = HaloxTheme.typography.title, color = HaloxTheme.colors.ink) }
        items(cases, key = { it.id }) { case ->
            CaseRow(case, onClick = { onOpenCase(case.id) })
        }
    }
}

@Composable
private fun CaseRow(case: ViolationCaseEntity, onClick: () -> Unit) {
    HaloxCard(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    ViolationType.entries.firstOrNull { it.name == case.type.name }?.displayName ?: case.type.name,
                    style = HaloxTheme.typography.label,
                    color = HaloxTheme.colors.ink,
                )
                MonoValue(case.plateString ?: "plate uncertain", emphasis = case.plateString != null)
            }
            StatusPill(case.status.name, statusLevel(case.status))
        }
        if (case.plateString != null) {
            ConfidenceTag(case.plateConfidence ?: 0f, case.plateValidated, Modifier.padding(top = 8.dp))
        }
    }
}

private fun statusLevel(status: CaseStatus): SignalLevel = when (status) {
    CaseStatus.CONFIRMED -> SignalLevel.CONFIRMED
    CaseStatus.DISMISSED -> SignalLevel.NEUTRAL
    CaseStatus.REVIEWED -> SignalLevel.NEUTRAL
    CaseStatus.OPEN -> SignalLevel.VIOLATION
}
