package com.haloxtraffic.feature.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.haloxtraffic.core.designsystem.component.EmptyState
import com.haloxtraffic.core.designsystem.component.StatusPill
import com.haloxtraffic.core.designsystem.theme.HaloxTheme
import com.haloxtraffic.core.designsystem.theme.SignalLevel
import com.haloxtraffic.core.model.ViolationType
import kotlin.math.abs

/**
 * Violation map + heatmap (§12.5), rendered on a Canvas so it works fully offline with no map
 * dependency: cases are plotted in their lat/lon bounding box, density is shaded as a heat layer, and
 * pins are coloured by validation. Tap a pin to open the case. (A MapLibre tile basemap can replace the
 * scatter later; the data/filters/interaction stay the same.)
 */
@Composable
fun MapScreen(
    onOpenCase: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val points by viewModel.points.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        // Filters.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip("All", filters.type == null) { viewModel.setType(null) }
            ViolationType.entries.forEach { t ->
                FilterChip(t.displayName, filters.type == t) { viewModel.setType(t) }
            }
            FilterChip("Uncertain", filters.onlyUncertain) { viewModel.toggleUncertain() }
        }

        if (points.isEmpty()) {
            EmptyState(
                Icons.Filled.Map, "No located cases",
                "Geo-tagged violations appear here as pins + a density heatmap.",
                Modifier.weight(1f),
            )
            return@Column
        }

        ViolationCanvas(points, Modifier.weight(1f).fillMaxWidth(), onOpenCase)
    }
}

@Composable
private fun ViolationCanvas(points: List<MapPoint>, modifier: Modifier, onOpenCase: (String) -> Unit) {
    val minLat = points.minOf { it.lat }
    val maxLat = points.maxOf { it.lat }
    val minLon = points.minOf { it.lon }
    val maxLon = points.maxOf { it.lon }
    val latSpan = (maxLat - minLat).takeIf { abs(it) > 1e-6 } ?: 1.0
    val lonSpan = (maxLon - minLon).takeIf { abs(it) > 1e-6 } ?: 1.0
    val pad = 0.12

    fun project(lat: Double, lon: Double, w: Float, h: Float): Offset {
        val nx = ((lon - minLon) / lonSpan) * (1 - 2 * pad) + pad
        val ny = (1.0 - (lat - minLat) / latSpan) * (1 - 2 * pad) + pad
        return Offset((nx * w).toFloat(), (ny * h).toFloat())
    }

    val violation = HaloxTheme.colors.violation
    val confirmed = HaloxTheme.colors.confirmed
    val degraded = HaloxTheme.colors.degraded
    val canvasBg = HaloxTheme.colors.surface

    Canvas(
        modifier
            .background(canvasBg)
            .pointerInput(points) {
                detectTapGestures { o ->
                    val nearest = points.minByOrNull { p ->
                        val pos = project(p.lat, p.lon, size.width.toFloat(), size.height.toFloat())
                        (pos - o).getDistanceSquared()
                    }
                    if (nearest != null) {
                        val pos = project(nearest.lat, nearest.lon, size.width.toFloat(), size.height.toFloat())
                        if ((pos - o).getDistance() < 48f) onOpenCase(nearest.caseId)
                    }
                }
            },
    ) {
        // Heat layer: translucent additive circles → overlaps brighten into hotspots.
        points.forEach { p ->
            drawCircle(violation.copy(alpha = 0.12f), radius = 40f, center = project(p.lat, p.lon, size.width, size.height))
        }
        // Pins on top, coloured by validation state.
        points.forEach { p ->
            val c = if (p.validated) confirmed else degraded
            val pos = project(p.lat, p.lon, size.width, size.height)
            drawCircle(c, radius = 7f, center = pos)
            drawCircle(Color.Black.copy(alpha = 0.5f), radius = 7f, center = pos, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    StatusPill(
        label = label,
        level = if (selected) SignalLevel.CONFIRMED else SignalLevel.NEUTRAL,
        modifier = Modifier.pointerInput(label) { detectTapGestures { onClick() } },
    )
}
