package com.haloxtraffic.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.haloxtraffic.core.designsystem.theme.HaloxTheme

/** Centered async-loading state with an optional caption. */
@Composable
fun LoadingState(modifier: Modifier = Modifier, caption: String? = null) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = HaloxTheme.colors.ink, strokeWidth = 2.dp)
        if (caption != null) {
            Text(
                caption,
                style = HaloxTheme.typography.dataSmall,
                color = HaloxTheme.colors.inkMuted,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** Empty state with an icon, headline and supporting line. */
@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    CenteredMessage(icon, title, message, HaloxTheme.colors.inkMuted, modifier)
}

/** Error state — amber, since an error is a degraded condition the operator must notice. */
@Composable
fun ErrorState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    CenteredMessage(icon, title, message, HaloxTheme.colors.degraded, modifier)
}

@Composable
private fun CenteredMessage(
    icon: ImageVector,
    title: String,
    message: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(40.dp))
        Text(
            title,
            style = HaloxTheme.typography.title,
            color = HaloxTheme.colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            message,
            style = HaloxTheme.typography.body,
            color = HaloxTheme.colors.inkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
