package com.haloxtraffic.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.haloxtraffic.core.designsystem.component.HairlineDivider
import com.haloxtraffic.core.designsystem.component.HaloxCard
import com.haloxtraffic.core.designsystem.component.OperationalButton
import com.haloxtraffic.core.designsystem.component.StatusPill
import com.haloxtraffic.core.designsystem.theme.HaloxTheme
import com.haloxtraffic.core.designsystem.theme.SignalLevel
import com.haloxtraffic.core.model.DeviceTier

/**
 * Settings (§12.8). Phase-1 surfaces the wired controls: device-tier override, bystander-blur default,
 * retention. FSM/ANPR thresholds + model management land with their phases.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = HaloxTheme.typography.title, color = HaloxTheme.colors.ink)

        HaloxCard {
            Text("DEVICE TIER OVERRIDE", style = HaloxTheme.typography.labelMicro, color = HaloxTheme.colors.inkFaint)
            Text(
                "Force a capability tier. Leave on AUTO to use the profiled tier.",
                style = HaloxTheme.typography.body,
                color = HaloxTheme.colors.inkMuted,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val current = settings?.tierOverride
                OperationalButton("Auto", { viewModel.setTierOverride(null) })
                DeviceTier.entries.forEach { tier ->
                    OperationalButton(tier.name, { viewModel.setTierOverride(tier) })
                }
                if (current != null) StatusPill(current.name, SignalLevel.CONFIRMED)
            }
        }

        HaloxCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.padding(end = 12.dp)) {
                    Text("Blur bystanders in exports", style = HaloxTheme.typography.label, color = HaloxTheme.colors.ink)
                    Text(
                        "Privacy: blur uninvolved faces in exported evidence by default.",
                        style = HaloxTheme.typography.body,
                        color = HaloxTheme.colors.inkMuted,
                    )
                }
                Switch(
                    checked = settings?.bystanderBlurDefault ?: true,
                    onCheckedChange = { viewModel.setBystanderBlur(it) },
                )
            }
        }

        HaloxCard {
            Text("RETENTION", style = HaloxTheme.typography.labelMicro, color = HaloxTheme.colors.inkFaint)
            Text(
                "Sealed evidence is never auto-purged before successful sync + integrity confirmation.",
                style = HaloxTheme.typography.body,
                color = HaloxTheme.colors.inkMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
            HairlineDivider(Modifier.padding(vertical = 8.dp))
            Text(
                "Retention: ${(settings?.retentionDays ?: 0).let { if (it == 0) "never auto-purge" else "$it days" }}",
                style = HaloxTheme.typography.dataValue,
                color = HaloxTheme.colors.ink,
            )
        }
    }
}
