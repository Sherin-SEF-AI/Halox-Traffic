package com.haloxtraffic.feature.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.haloxtraffic.core.data.analytics.CaseAnalytics
import com.haloxtraffic.core.designsystem.component.ButtonKind
import com.haloxtraffic.core.designsystem.component.HairlineDivider
import com.haloxtraffic.core.designsystem.component.HaloxCard
import com.haloxtraffic.core.designsystem.component.OperationalButton
import com.haloxtraffic.core.designsystem.component.TelemetryRow
import com.haloxtraffic.core.export.ExportShare
import com.haloxtraffic.core.designsystem.theme.HaloxTheme

/** Reports + analytics (§12.6): violation breakdowns + exports (e-challan/PDF live in the case file). */
@Composable
fun ReportsScreen(
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val analytics by viewModel.analytics.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Reports", style = HaloxTheme.typography.title, color = HaloxTheme.colors.ink)

        HaloxCard {
            Text("SUMMARY", style = HaloxTheme.typography.labelMicro, color = HaloxTheme.colors.inkFaint)
            TelemetryRow("CASES", analytics.total.toString(), valueEmphasis = true)
            TelemetryRow("VALIDATED", pct(analytics.validatedRate))
            TelemetryRow("UNCERTAIN", pct(analytics.uncertainRate))
        }

        if (analytics.byType.isNotEmpty()) {
            HaloxCard {
                Text("BY TYPE", style = HaloxTheme.typography.labelMicro, color = HaloxTheme.colors.inkFaint)
                analytics.byType.entries.sortedByDescending { it.value }.forEach { (type, count) ->
                    TelemetryRow(type.displayName, count.toString())
                }
            }
        }

        if (analytics.repeatPlates.isNotEmpty()) {
            HaloxCard {
                Text("REPEAT OFFENDERS", style = HaloxTheme.typography.labelMicro, color = HaloxTheme.colors.inkFaint)
                analytics.repeatPlates.take(8).forEach { (plate, count) -> TelemetryRow(plate, "×$count") }
            }
        }

        if (analytics.byHour.isNotEmpty()) {
            HaloxCard {
                Text("BY HOUR (UTC)", style = HaloxTheme.typography.labelMicro, color = HaloxTheme.colors.inkFaint)
                HourHistogram(analytics)
            }
        }

        OperationalButton(
            text = "Export CSV index",
            onClick = { viewModel.exportCsvIndex { file -> ExportShare.share(context, file, "text/csv") } },
            kind = ButtonKind.PRIMARY,
            icon = Icons.Filled.Download,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HourHistogram(analytics: CaseAnalytics) {
    val max = (analytics.byHour.values.maxOrNull() ?: 1).coerceAtLeast(1)
    Column(Modifier.padding(top = 6.dp)) {
        analytics.byHour.entries.sortedBy { it.key }.forEach { (hour, count) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("%02d:00".format(hour), style = HaloxTheme.typography.dataSmall, color = HaloxTheme.colors.inkMuted)
                Text(
                    "█".repeat((count * 16 / max).coerceAtLeast(1)),
                    style = HaloxTheme.typography.dataSmall,
                    color = HaloxTheme.colors.confirmed,
                )
                Text(count.toString(), style = HaloxTheme.typography.dataSmall, color = HaloxTheme.colors.inkMuted)
            }
        }
        HairlineDivider(Modifier.padding(top = 6.dp))
    }
}

private fun pct(v: Float): String = "${(v * 100).toInt()}%"
