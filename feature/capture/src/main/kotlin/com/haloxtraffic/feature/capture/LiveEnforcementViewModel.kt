package com.haloxtraffic.feature.capture

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.camera.core.Preview
import com.haloxtraffic.core.data.entity.SessionEntity
import com.haloxtraffic.core.data.repository.SessionRepository
import com.haloxtraffic.core.data.settings.SettingsRepository
import com.haloxtraffic.core.model.DeviceProfile
import com.haloxtraffic.core.model.DeviceTier
import com.haloxtraffic.core.model.GeoFix
import com.haloxtraffic.core.model.MountMode
import com.haloxtraffic.core.model.TimeTrust
import com.haloxtraffic.core.sensors.camera.CameraController
import com.haloxtraffic.core.sensors.location.LocationSource
import com.haloxtraffic.core.model.BoundingBox
import com.haloxtraffic.core.model.InferenceDelegate
import com.haloxtraffic.core.sensors.profile.AdaptiveRuntimeController
import com.haloxtraffic.core.sensors.profile.DeviceProfiler
import com.haloxtraffic.core.sensors.time.TimeSource
import com.haloxtraffic.core.model.PlateRead
import com.haloxtraffic.feature.anpr.AnprController
import com.haloxtraffic.feature.anpr.OcrStatus
import com.haloxtraffic.feature.detection.DetectionController
import com.haloxtraffic.feature.detection.DetectorStatus
import com.haloxtraffic.feature.violations.ActiveViolation
import com.haloxtraffic.feature.violations.ViolationController
import com.haloxtraffic.feature.violations.ViolationEvent
import kotlinx.coroutines.flow.SharedFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject

/** UI state for the Live Enforcement screen — a stable, glanceable telemetry surface (§12.2). */
data class CaptureUiState(
    val tier: DeviceTier = DeviceTier.LOW,
    val deviceSummary: String = "",
    val mountMode: MountMode = MountMode.HANDHELD,
    val cameraBound: Boolean = false,
    val fps: Float = 0f,
    val targetFps: Int = 0,
    val analysisRes: String = "—",
    val geo: GeoFix? = null,
    val timeTrust: TimeTrust = TimeTrust.UNTRUSTED,
    val degraded: Boolean = false,
    val thermalHeadroom: Float = 0f,
    val sessionId: String? = null,
    val sessionCount: Int = 0,
    val paused: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val lastPlate: String? = null,
    val lastPlateConfidence: Float = 0f,
    val lastPlateValidated: Boolean = false,
    val ocrStatus: OcrStatus = OcrStatus.IDLE,
    // Detection (Phase 2)
    val detectorStatus: DetectorStatus = DetectorStatus.IDLE,
    val boxes: List<BoundingBox> = emptyList(),
    val detectorLabels: List<String> = emptyList(),
    val preprocessMs: Long = 0,
    val inferenceMs: Long = 0,
    val activeDelegate: InferenceDelegate? = null,
    // Violations (Phase 3)
    val activeViolations: List<ActiveViolation> = emptyList(),
    val sessionViolations: Int = 0,
)

@HiltViewModel
class LiveEnforcementViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceProfiler: DeviceProfiler,
    private val adaptiveRuntime: AdaptiveRuntimeController,
    private val cameraController: CameraController,
    private val locationSource: LocationSource,
    private val timeSource: TimeSource,
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val detectionController: DetectionController,
    private val violationController: ViolationController,
    private val anprController: AnprController,
) : ViewModel() {

    /** Plate reads keyed by track, retained for evidence sealing (Phase 5). */
    private val plateResults = mutableMapOf<Long, PlateRead>()

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    /** New COMMITs — collected by the screen to fire a haptic/discrete cue. */
    val violationEvents: SharedFlow<ViolationEvent> = violationController.events

    private var profile: DeviceProfile? = null

    init {
        profileDevice()
        observeRuntime()
        observeCameraMetrics()
        observeDetection()
        observeViolations()
    }

    private fun observeViolations() {
        viewModelScope.launch {
            violationController.active.collect { a -> _state.value = _state.value.copy(activeViolations = a) }
        }
        viewModelScope.launch {
            violationController.committedCount.collect { c -> _state.value = _state.value.copy(sessionViolations = c) }
        }
        // On each COMMIT, run ANPR off the hot path: crop plates for the offending track → recognise.
        viewModelScope.launch {
            violationController.events.collect { event ->
                val crops = detectionController.cropPlatesForTrack(event.track.box)
                val plate = anprController.recognizePlate(crops)
                plateResults[event.trackId] = plate
                _state.value = _state.value.copy(
                    lastPlate = plate.plate,
                    lastPlateConfidence = plate.confidence,
                    lastPlateValidated = plate.validated,
                )
            }
        }
        viewModelScope.launch {
            anprController.status.collect { s -> _state.value = _state.value.copy(ocrStatus = s) }
        }
    }

    private fun observeDetection() {
        _state.value = _state.value.copy(detectorLabels = detectionController.labels)
        viewModelScope.launch {
            detectionController.status.collect { s -> _state.value = _state.value.copy(detectorStatus = s) }
        }
        viewModelScope.launch {
            detectionController.frames.collect { f ->
                if (f != null) {
                    // Stage 2+3: track + run the FSM bank on this frame's detections.
                    violationController.onFrame(f.boxes)
                    _state.value = _state.value.copy(
                        boxes = f.boxes,
                        preprocessMs = f.preprocessMs,
                        inferenceMs = f.inferenceMs,
                        activeDelegate = f.delegate,
                    )
                }
            }
        }
    }

    private fun profileDevice() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val p = deviceProfiler.profile()
            profile = p
            val tier = settings.tierOverride ?: p.tier
            adaptiveRuntime.reset(tier)
            _state.value = _state.value.copy(
                tier = tier,
                deviceSummary = p.summary,
                thermalHeadroom = p.thermalHeadroom,
                targetFps = adaptiveRuntime.config.value.targetFps,
            )
            // Provision + load the detector and OCR recognizer for this tier (off the hot path). With
            // placeholder model URLs these resolve to NO_MODEL; supply real assets in ModelRegistry.
            detectionController.start(tier)
            anprController.start(tier)
        }
    }

    private fun observeRuntime() {
        viewModelScope.launch {
            adaptiveRuntime.config.collect { cfg ->
                _state.value = _state.value.copy(targetFps = cfg.targetFps)
            }
        }
        viewModelScope.launch {
            adaptiveRuntime.degraded.collect { d -> _state.value = _state.value.copy(degraded = d) }
        }
    }

    private fun observeCameraMetrics() {
        viewModelScope.launch {
            cameraController.metrics.collect { m ->
                _state.value = _state.value.copy(
                    cameraBound = m.bound,
                    fps = m.fps,
                    analysisRes = if (m.analysisWidth > 0) "${m.analysisWidth}×${m.analysisHeight}" else "—",
                )
            }
        }
    }

    fun setPermissions(camera: Boolean, location: Boolean) {
        _state.value = _state.value.copy(hasCameraPermission = camera, hasLocationPermission = location)
        if (location) observeLocation()
    }

    fun setMountMode(mode: MountMode) {
        _state.value = _state.value.copy(mountMode = mode)
    }

    fun bindCamera(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        if (!_state.value.hasCameraPermission) return
        viewModelScope.launch {
            // Stage 1 detection runs in this analyzer (on the camera analysis thread). When the model
            // isn't ready it simply skips frames — never blocking preview and never fabricating boxes.
            cameraController.bind(lifecycleOwner, surfaceProvider, detectionController.analyzer())
        }
    }

    private fun observeLocation() {
        viewModelScope.launch {
            runCatching {
                locationSource.fixes().collect { fix ->
                    timeSource.updateGpsAnchor(fix.obtainedAtMs)
                    _state.value = _state.value.copy(
                        geo = fix,
                        timeTrust = timeSource.now().trust,
                    )
                }
            }.onFailure { Timber.e(it, "Location collection failed") }
        }
    }

    fun startSession() {
        if (_state.value.sessionId != null) return
        violationController.reset() // singleton outlives the ViewModel; clear prior-session state
        plateResults.clear()
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val id = UUID.randomUUID().toString()
            sessionRepository.start(
                SessionEntity(
                    id = id,
                    jurisdictionId = settings.jurisdictionId,
                    officerId = settings.officerId,
                    mountMode = _state.value.mountMode,
                    startedAt = System.currentTimeMillis(),
                    endedAt = null,
                    deviceTier = _state.value.tier,
                    deviceMeta = _state.value.deviceSummary,
                    modelVersionsJson = MODEL_VERSIONS_PLACEHOLDER,
                ),
            )
            _state.value = _state.value.copy(sessionId = id)
            Timber.i("Session started: $id")
        }
    }

    fun togglePause() {
        _state.value = _state.value.copy(paused = !_state.value.paused)
    }

    /** Manual full-res capture override (§12.2) → app-private store. */
    fun captureManual(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val dir = File(context.filesDir, "captures").apply { mkdirs() }
            val file = File(dir, "manual_${System.currentTimeMillis()}.jpg")
            val result = cameraController.captureStill(file)
            onResult(result.isSuccess)
        }
    }

    fun endSession(onEnded: () -> Unit) {
        val id = _state.value.sessionId
        viewModelScope.launch {
            if (id != null) sessionRepository.end(id, System.currentTimeMillis())
            cameraController.unbind()
            detectionController.stop()
            violationController.reset()
            _state.value = _state.value.copy(
                sessionId = null, paused = false, boxes = emptyList(), activeViolations = emptyList(),
            )
            onEnded()
        }
    }

    override fun onCleared() {
        cameraController.unbind()
        detectionController.stop()
        super.onCleared()
    }

    private companion object {
        const val MODEL_VERSIONS_PLACEHOLDER = """{"detector":"pending","ocr":"pending","vlm":"pending"}"""
    }
}
