package com.haloxtraffic.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.haloxtraffic.core.designsystem.theme.HaloxTheme

/** Visual prominence of an [OperationalButton]. */
enum class ButtonKind { PRIMARY, NEUTRAL, DANGER }

/**
 * Flat, square-ish operational button — no gloss, no elevation. Meets the 48dp touch target and
 * carries an optional leading icon so actions aren't label-only.
 */
@Composable
fun OperationalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.NEUTRAL,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(HaloxTheme.dimens.radiusSm)
    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null)
            Text(
                text.uppercase(),
                style = HaloxTheme.typography.label,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
    val mod = modifier.heightIn(min = HaloxTheme.dimens.touchTarget)

    when (kind) {
        ButtonKind.PRIMARY -> Button(
            onClick = onClick, enabled = enabled, shape = shape, modifier = mod,
            colors = ButtonDefaults.buttonColors(
                containerColor = HaloxTheme.colors.confirmed,
                contentColor = HaloxTheme.colors.onSignal,
            ),
        ) { content() }

        ButtonKind.DANGER -> Button(
            onClick = onClick, enabled = enabled, shape = shape, modifier = mod,
            colors = ButtonDefaults.buttonColors(
                containerColor = HaloxTheme.colors.violation,
                contentColor = HaloxTheme.colors.onSignal,
            ),
        ) { content() }

        ButtonKind.NEUTRAL -> OutlinedButton(
            onClick = onClick, enabled = enabled, shape = shape, modifier = mod,
            border = BorderStroke(HaloxTheme.dimens.hairline, HaloxTheme.colors.hairlineStrong),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = HaloxTheme.colors.ink),
        ) { content() }
    }
}

/**
 * Fixed bottom action band for the live screen — primary controls live here, thumb-reachable, in a
 * stable position so muscle memory works under field conditions.
 */
@Composable
fun BottomActionBand(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier
            .heightIn(min = HaloxTheme.dimens.actionBandHeight)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
