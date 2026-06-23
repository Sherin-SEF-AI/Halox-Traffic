package com.haloxtraffic.feature.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.haloxtraffic.core.designsystem.component.BottomActionBand
import com.haloxtraffic.core.designsystem.component.ButtonKind
import com.haloxtraffic.core.designsystem.component.CameraScrim
import com.haloxtraffic.core.designsystem.component.OperationalButton
import com.haloxtraffic.core.designsystem.component.PermissionRationaleCard
import com.haloxtraffic.core.designsystem.component.StatusPill
import com.haloxtraffic.core.designsystem.theme.HaloxTheme
import com.haloxtraffic.core.designsystem.theme.SignalLevel
import com.haloxtraffic.core.model.NormPoint

/** Draw a junction's stop-line / signal ROI / lanes over the live camera (§12.3). */
@Composable
fun JunctionConfigScreen(
    onBack: () -> Unit,
    viewModel: JunctionConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.setPermission(it)
    }
    LaunchedEffect(Unit) {
        viewModel.setPermission(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (!state.hasCameraPermission) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), Alignment.Center) {
                PermissionRationaleCard(
                    title = "Camera needed",
                    rationale = "Point the camera at the junction to mark the stop-line and signal region.",
                    actionLabel = "Grant camera",
                    onGrant = { launcher.launch(Manifest.permission.CAMERA) },
                )
            }
            return@Box
        }

        CameraPreview { pv, owner -> viewModel.bindCamera(owner, pv.surfaceProvider) }

        // Drawing overlay + tap capture.
        Canvas(
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures { o -> viewModel.addPoint(o.x / size.width, o.y / size.height) }
            },
        ) {
            state.stopLine?.let { drawPolygon(it.points, Color(0xFF2ECC71), close = true) }
            state.signalRoi?.let { drawPolygon(it.points, Color(0xFFFFB020), close = true) }
            state.laneBoundaries.forEach { drawPolygon(it.polyline, Color(0xFF4DA3FF), close = false) }
            drawInProgress(state.currentPoints)
        }

        // Top: element selector.
        Box(Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip("Stop line", ConfigMode.STOP_LINE, state.mode) { viewModel.setMode(it) }
                    ModeChip("Signal ROI", ConfigMode.SIGNAL_ROI, state.mode) { viewModel.setMode(it) }
                    ModeChip("Lane", ConfigMode.LANE, state.mode) { viewModel.setMode(it) }
                }
                if (state.saved) StatusPill("SAVED", SignalLevel.CONFIRMED)
            }
        }

        // Bottom action band.
        Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
            CameraScrim(Modifier.matchParentSize(), fromTop = false)
            BottomActionBand(Modifier.fillMaxWidth().safeDrawingPadding()) {
                OperationalButton("Undo", viewModel::undo)
                OperationalButton("Close shape", viewModel::closeShape)
                OperationalButton("Save", viewModel::save, kind = ButtonKind.PRIMARY)
                OperationalButton("Back", onBack)
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, mode: ConfigMode, current: ConfigMode, onSelect: (ConfigMode) -> Unit) {
    StatusPill(
        label = label,
        level = if (mode == current) SignalLevel.CONFIRMED else SignalLevel.NEUTRAL,
        modifier = Modifier.pointerInput(mode) { detectTapGestures { onSelect(mode) } },
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPolygon(points: List<NormPoint>, color: Color, close: Boolean) {
    if (points.size < 2) {
        points.forEach { drawCircle(color, 6f, Offset(it.x * size.width, it.y * size.height)) }
        return
    }
    val pts = points.map { Offset(it.x * size.width, it.y * size.height) }
    for (i in 0 until pts.size - 1) drawLine(color, pts[i], pts[i + 1], strokeWidth = 4f)
    if (close) drawLine(color, pts.last(), pts.first(), strokeWidth = 4f)
    pts.forEach { drawCircle(color, 5f, it) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawInProgress(points: List<NormPoint>) {
    val pts = points.map { Offset(it.x * size.width, it.y * size.height) }
    pts.forEach { drawCircle(Color.White, 6f, it) }
    for (i in 0 until pts.size - 1) drawLine(Color.White, pts[i], pts[i + 1], strokeWidth = 2f)
    if (pts.isNotEmpty()) drawCircle(Color.White, 9f, pts.last(), style = Stroke(width = 2f))
}

@Composable
private fun CameraPreview(onReady: (PreviewView, androidx.lifecycle.LifecycleOwner) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val preview = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    androidx.compose.ui.viewinterop.AndroidView(factory = { preview }, modifier = Modifier.fillMaxSize())
    LaunchedEffect(preview) { onReady(preview, owner) }
}
