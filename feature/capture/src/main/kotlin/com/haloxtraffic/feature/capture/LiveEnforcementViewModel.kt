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
import com.haloxtraffic.core.sensors.profile.AdaptiveRuntimeController
import com.haloxtraffic.core.sensors.profile.DeviceProfiler
import com.haloxtraffic.core.sensors.time.TimeSource
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
    val lastPlate: String? = null, // populated once ANPR lands (Phase 4)
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
) : ViewModel() {

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    private var profile: DeviceProfile? = null

    init {
        profileDevice()
        observeRuntime()
        observeCameraMetrics()
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
            // Phase 1: analyzer is a no-op (closes the frame). Detection plugs in here in Phase 2.
            cameraController.bind(lifecycleOwner, surfaceProvider)
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
            _state.value = _state.value.copy(sessionId = null, paused = false)
            onEnded()
        }
    }

    override fun onCleared() {
        cameraController.unbind()
        super.onCleared()
    }

    private companion object {
        const val MODEL_VERSIONS_PLACEHOLDER = """{"detector":"pending","ocr":"pending","vlm":"pending"}"""
    }
}
