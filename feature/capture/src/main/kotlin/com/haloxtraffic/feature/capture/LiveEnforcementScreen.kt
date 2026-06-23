package com.haloxtraffic.feature.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.haloxtraffic.core.designsystem.component.BottomActionBand
import com.haloxtraffic.core.designsystem.component.ButtonKind
import com.haloxtraffic.core.designsystem.component.CameraScrim
import com.haloxtraffic.core.designsystem.component.OperationalButton
import com.haloxtraffic.core.designsystem.component.PermissionRationaleCard

@Composable
fun LiveEnforcementScreen(
    onSessionEnded: () -> Unit,
    viewModel: LiveEnforcementViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    // Discrete haptic cue on each committed violation (§11/§12.2).
    LaunchedEffect(Unit) {
        viewModel.violationEvents.collect {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun hasPermission(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        viewModel.setPermissions(
            camera = grants[Manifest.permission.CAMERA] ?: hasPermission(Manifest.permission.CAMERA),
            location = grants[Manifest.permission.ACCESS_FINE_LOCATION]
                ?: hasPermission(Manifest.permission.ACCESS_FINE_LOCATION),
        )
    }

    LaunchedEffect(Unit) {
        val cam = hasPermission(Manifest.permission.CAMERA)
        val loc = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        viewModel.setPermissions(cam, loc)
        if (cam) viewModel.startSession()
    }

    Box(Modifier.fillMaxSize()) {
        if (!state.hasCameraPermission) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), Alignment.Center) {
                PermissionRationaleCard(
                    title = "Camera & location required",
                    rationale = "HaloxTraffic needs the camera to detect violations and read plates, and " +
                        "location to geo-tag evidence. All processing stays on this device.",
                    actionLabel = "Grant access",
                    onGrant = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                            ),
                        )
                    },
                )
            }
            return@Box
        }

        CameraPreview(onSurfaceReady = { previewView, lifecycleOwner ->
            viewModel.bindCamera(lifecycleOwner, previewView.surfaceProvider)
        })

        if (!state.paused) {
            DetectionOverlay(
                boxes = state.boxes,
                labels = state.detectorLabels,
                violations = state.activeViolations,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Top scrim + telemetry HUD.
        Box(Modifier.fillMaxWidth().height(220.dp).align(Alignment.TopCenter)) {
            CameraScrim(Modifier.matchParentSize())
            TelemetryHud(state, Modifier.safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp))
        }

        // Bottom action band — wraps its content; the scrim matches the band (not the whole screen).
        Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
            CameraScrim(Modifier.matchParentSize(), fromTop = false)
            BottomActionBand(
                Modifier.fillMaxWidth().safeDrawingPadding(),
            ) {
                OperationalButton(
                    text = if (state.paused) "Resume" else "Pause",
                    onClick = viewModel::togglePause,
                    modifier = Modifier.weight(1f),
                )
                OperationalButton(
                    text = "Capture",
                    onClick = { viewModel.captureManual { } },
                    kind = ButtonKind.PRIMARY,
                    modifier = Modifier.weight(1f),
                )
                OperationalButton(
                    text = "End",
                    onClick = { viewModel.endSession(onSessionEnded) },
                    kind = ButtonKind.DANGER,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CameraPreview(
    onSurfaceReady: (PreviewView, androidx.lifecycle.LifecycleOwner) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
    LaunchedEffect(previewView) { onSurfaceReady(previewView, lifecycleOwner) }
}
