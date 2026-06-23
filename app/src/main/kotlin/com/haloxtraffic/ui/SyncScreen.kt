package com.haloxtraffic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.haloxtraffic.core.data.repository.IntegrityResult
import com.haloxtraffic.core.designsystem.component.ButtonKind
import com.haloxtraffic.core.designsystem.component.HairlineDivider
import com.haloxtraffic.core.designsystem.component.HaloxCard
import com.haloxtraffic.core.designsystem.component.MonoValue
import com.haloxtraffic.core.designsystem.component.OperationalButton
import com.haloxtraffic.core.designsystem.component.StatusPill
import com.haloxtraffic.core.designsystem.component.TelemetryRow
import com.haloxtraffic.core.designsystem.theme.HaloxTheme
import com.haloxtraffic.core.designsystem.theme.SignalLevel
import com.haloxtraffic.core.evidence.ChainVerification

/** Sync / Evidence (§12.7): queue status, integrity self-check, storage, optional Vahan cross-reference. */
@Composable
fun SyncScreen(
    modifier: Modifier = Modifier,
    viewModel: SyncViewModel = hiltViewModel(),
) {
    val pending by viewModel.pendingCount.collectAsStateWithLifecycle()
    val integrity by viewModel.integrity.collectAsStateWithLifecycle()
    val storage by viewModel.storageBytes.collectAsStateWithLifecycle()
    val vahan by viewModel.vahan.collectAsStateWithLifecycle()

    Column(
        modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Sync & Evidence", style = HaloxTheme.typography.title, color = HaloxTheme.colors.ink)

        HaloxCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TelemetryRow("PENDING", pending.toString(), valueEmphasis = pending > 0)
            }
            OperationalButton(
                "Run sync now", viewModel::runSyncNow,
                kind = ButtonKind.PRIMARY, modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "Offline-first: cases sync opportunistically on connectivity; uploads are idempotent.",
                style = HaloxTheme.typography.body, color = HaloxTheme.colors.inkMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        HaloxCard {
            Text("CHAIN INTEGRITY", style = HaloxTheme.typography.labelMicro, color = HaloxTheme.colors.inkFaint)
            integrity?.let { IntegrityResultRow(it) }
            OperationalButton("Verify sealed store", viewModel::checkIntegrity, modifier = Modifier.padding(top = 8.dp))
        }

        HaloxCard {
            TelemetryRow("STORAGE", formatBytes(storage))
        }

        HaloxCard {
            Text("VAHAN LOOKUP (OPTIONAL)", style = HaloxTheme.typography.labelMicro, color = HaloxTheme.colors.inkFaint)
            var plate by remember { mutableStateOf("") }
            OutlinedTextField(
                value = plate, onValueChange = { plate = it }, label = { Text("Plate") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OperationalButton(
                if (vahan.querying) "Looking up…" else "Cross-reference",
                { if (plate.isNotBlank()) viewModel.lookupVahan(plate) },
                modifier = Modifier.padding(top = 8.dp),
            )
            vahan.result?.let {
                HairlineDivider(Modifier.padding(vertical = 8.dp))
                MonoValue("found=${it.found}  ${it.ownerHint ?: ""} ${it.registeredState ?: ""}", emphasis = true)
            }
            vahan.error?.let {
                MonoValue(it, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun IntegrityResultRow(result: IntegrityResult) {
    val (label, level) = when {
        result.ok -> "VERIFIED" to SignalLevel.CONFIRMED
        result.chain is ChainVerification.Broken ->
            "CHAIN BROKEN @${(result.chain as ChainVerification.Broken).index}" to SignalLevel.VIOLATION
        !result.allSignaturesValid -> "SIGNATURE INVALID" to SignalLevel.VIOLATION
        else -> "UNKNOWN" to SignalLevel.DEGRADED
    }
    Row(Modifier.padding(top = 6.dp)) { StatusPill(label, level) }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1e9)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1e6)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1e3)
    else -> "$bytes B"
}
