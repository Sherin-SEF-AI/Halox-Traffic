package com.haloxtraffic.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.haloxtraffic.core.designsystem.theme.HaloxTheme
import com.haloxtraffic.core.designsystem.theme.SignalLevel

/**
 * Operational status chip. Colour is paired with an icon and label so the state is never conveyed by
 * colour alone (§11/§14 accessibility). [icon] defaults to a level-appropriate glyph.
 */
@Composable
fun StatusPill(
    label: String,
    level: SignalLevel,
    modifier: Modifier = Modifier,
    icon: ImageVector? = level.defaultIcon(),
) {
    val color = HaloxTheme.colors.signal(level)
    Row(
        modifier = modifier
            .background(HaloxTheme.colors.signalDim(level), RoundedCornerShape(HaloxTheme.dimens.radiusSm))
            .border(HaloxTheme.dimens.hairline, color, RoundedCornerShape(HaloxTheme.dimens.radiusSm))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = "$label, ${level.name.lowercase()}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        }
        Text(label, color = color, style = HaloxTheme.typography.labelMicro)
    }
}

/** Plate-read confidence tag. Validated → confirmed/green; uncertain → degraded/amber. */
@Composable
fun ConfidenceTag(confidence: Float, validated: Boolean, modifier: Modifier = Modifier) {
    val level = if (validated) SignalLevel.CONFIRMED else SignalLevel.DEGRADED
    val pct = (confidence.coerceIn(0f, 1f) * 100).toInt()
    StatusPill(
        label = if (validated) "VALID $pct%" else "UNCERTAIN $pct%",
        level = level,
        modifier = modifier,
    )
}

/** Active-violation badge — always red while a violation is live. */
@Composable
fun ViolationBadge(violationName: String, modifier: Modifier = Modifier) {
    StatusPill(label = violationName.uppercase(), level = SignalLevel.VIOLATION, modifier = modifier)
}

private fun SignalLevel.defaultIcon(): ImageVector? = when (this) {
    SignalLevel.CONFIRMED -> Icons.Filled.CheckCircle
    SignalLevel.VIOLATION -> Icons.Filled.Error
    SignalLevel.DEGRADED -> Icons.Filled.Warning
    SignalLevel.NEUTRAL -> null
}

@Preview(backgroundColor = 0xFF0A0B0C, showBackground = true)
@Composable
private fun IndicatorsPreview() {
    HaloxTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            ViolationBadge("No Helmet")
            ConfidenceTag(0.94f, validated = true)
            ConfidenceTag(0.52f, validated = false)
            StatusPill("GPS WEAK", SignalLevel.DEGRADED)
            StatusPill("SEALED", SignalLevel.CONFIRMED)
        }
    }
}
