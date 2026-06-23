package com.haloxtraffic.feature.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.haloxtraffic.core.designsystem.component.PlateReadout
import com.haloxtraffic.core.designsystem.component.StatusPill
import com.haloxtraffic.core.designsystem.component.TelemetryRow
import com.haloxtraffic.core.designsystem.theme.HaloxTheme
import com.haloxtraffic.core.designsystem.theme.SignalLevel
import com.haloxtraffic.core.model.GeoFix
import com.haloxtraffic.core.model.TimeTrust
import com.haloxtraffic.feature.detection.DetectorStatus

/**
 * The live telemetry HUD (§12.2). Reads like an instrument: fixed zones, monospace tabular values, one
 * unambiguous status pill per operational condition. The last-plate slot stays empty until ANPR lands.
 */
@Composable
fun TelemetryHud(state: CaptureUiState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Status pills row — paused / degraded / GPS / time-trust.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusPill(state.tier.name, SignalLevel.NEUTRAL)
            if (state.paused) StatusPill("PAUSED", SignalLevel.DEGRADED)
            if (state.degraded) StatusPill("THROTTLED", SignalLevel.DEGRADED)
            StatusPill(
                label = if (state.timeTrust == TimeTrust.TRUSTED) "TIME OK" else "TIME?",
                level = if (state.timeTrust == TimeTrust.TRUSTED) SignalLevel.CONFIRMED else SignalLevel.DEGRADED,
            )
            gpsPill(state.geo)
            detectorPill(state.detectorStatus)
        }

        if (state.lastPlate != null) PlateReadout(state.lastPlate)

        TelemetryRow("FPS", "%.1f / %d".format(state.fps, state.targetFps), valueEmphasis = true)
        TelemetryRow("DETECT", detectText(state))
        TelemetryRow("OBJECTS", state.boxes.size.toString())
        TelemetryRow("RES", state.analysisRes)
        TelemetryRow("GPS", geoText(state.geo))
        TelemetryRow("MOUNT", state.mountMode.name)
        TelemetryRow("THERM", thermalText(state.thermalHeadroom))
        TelemetryRow("DEVICE", state.deviceSummary)
    }
}

@Composable
private fun detectorPill(status: DetectorStatus) {
    val level = when (status) {
        DetectorStatus.RUNNING -> SignalLevel.CONFIRMED
        DetectorStatus.NO_MODEL, DetectorStatus.ERROR -> SignalLevel.DEGRADED
        else -> SignalLevel.NEUTRAL
    }
    StatusPill(status.name.replace('_', ' '), level)
}

private fun detectText(state: CaptureUiState): String =
    if (state.detectorStatus == DetectorStatus.RUNNING) {
        "pre ${state.preprocessMs}ms · inf ${state.inferenceMs}ms · ${state.activeDelegate?.name ?: "—"}"
    } else {
        state.detectorStatus.name.lowercase().replace('_', ' ')
    }

@Composable
private fun gpsPill(geo: GeoFix?) {
    when {
        geo == null -> StatusPill("NO GPS", SignalLevel.DEGRADED)
        geo.isWeak -> StatusPill("GPS WEAK", SignalLevel.DEGRADED)
        else -> StatusPill("GPS LOCK", SignalLevel.CONFIRMED)
    }
}

private fun geoText(geo: GeoFix?): String =
    if (geo == null) "—" else "%.5f, %.5f  ±%.0fm".format(geo.lat, geo.lon, geo.accuracyM)

private fun thermalText(h: Float): String = when {
    h < 0.3f -> "nominal"
    h < 0.6f -> "warm"
    else -> "throttling"
}
