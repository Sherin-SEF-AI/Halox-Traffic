package com.haloxtraffic.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.haloxtraffic.core.designsystem.theme.HaloxTheme

/** Flat matte card — a graphite plane with a hairline border. The base surface for all cards. */
@Composable
fun HaloxCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(HaloxTheme.colors.surfaceRaised, RoundedCornerShape(HaloxTheme.dimens.radiusMd))
            .border(
                HaloxTheme.dimens.hairline,
                HaloxTheme.colors.hairline,
                RoundedCornerShape(HaloxTheme.dimens.radiusMd),
            )
            .padding(16.dp),
    ) { content() }
}

/**
 * Onboarding device-tier reveal: the assigned tier as a confident result, with the *why* (RAM / SoC /
 * delegate) shown as monospace evidence beneath it.
 */
@Composable
fun TierResultCard(tierName: String, reasoning: List<String>, modifier: Modifier = Modifier) {
    HaloxCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Memory, contentDescription = null, tint = HaloxTheme.colors.confirmed)
            Column {
                Text("ASSIGNED TIER", style = HaloxTheme.typography.labelMicro, color = HaloxTheme.colors.inkFaint)
                Text(tierName, style = HaloxTheme.typography.title, color = HaloxTheme.colors.ink)
            }
        }
        HairlineDivider(Modifier.padding(vertical = 12.dp))
        reasoning.forEach { line -> MonoValue(line, modifier = Modifier.padding(vertical = 2.dp)) }
    }
}

/** Permission rationale, shown before the raw system prompt so the ask has context. */
@Composable
fun PermissionRationaleCard(
    title: String,
    rationale: String,
    actionLabel: String,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HaloxCard(modifier) {
        Text(title, style = HaloxTheme.typography.title, color = HaloxTheme.colors.ink)
        Text(
            rationale,
            style = HaloxTheme.typography.body,
            color = HaloxTheme.colors.inkMuted,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        OperationalButton(actionLabel, onGrant, kind = ButtonKind.PRIMARY)
    }
}

/** Model-provisioning progress (download → verify → cache), one row per model. */
@Composable
fun ProvisioningProgress(modelName: String, progress: Float, status: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(modelName, style = HaloxTheme.typography.label, color = HaloxTheme.colors.ink)
            MonoValue(status)
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            color = HaloxTheme.colors.confirmed,
            trackColor = HaloxTheme.colors.surfaceHigh,
        )
    }
}

@Preview(backgroundColor = 0xFF0A0B0C, showBackground = true)
@Composable
private fun CardsPreview() {
    HaloxTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TierResultCard(
                "HIGH",
                listOf("ram=8192MB", "soc=Snapdragon 8 Gen 2", "gpu=yes nnapi=yes", "vlm=enabled"),
            )
            ProvisioningProgress("yolo26s.tflite", 0.62f, "verifying")
        }
    }
}
