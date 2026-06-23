package com.haloxtraffic.feature.map

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.camera.core.Preview
import com.haloxtraffic.core.data.entity.JurisdictionEntity
import com.haloxtraffic.core.data.repository.JurisdictionRepository
import com.haloxtraffic.core.data.settings.SettingsRepository
import com.haloxtraffic.core.model.JunctionGeometry
import com.haloxtraffic.core.model.LaneBoundary
import com.haloxtraffic.core.model.NormPoint
import com.haloxtraffic.core.model.Polygon
import com.haloxtraffic.core.sensors.camera.CameraController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/** Which element the user is currently drawing over the camera view. */
enum class ConfigMode { STOP_LINE, SIGNAL_ROI, LANE }

data class JunctionConfigState(
    val mode: ConfigMode = ConfigMode.STOP_LINE,
    val currentPoints: List<NormPoint> = emptyList(),
    val stopLine: Polygon? = null,
    val signalRoi: Polygon? = null,
    val laneBoundaries: List<LaneBoundary> = emptyList(),
    val hasCameraPermission: Boolean = false,
    val saved: Boolean = false,
)

/**
 * Captures a junction's enforcement geometry over the live camera (§12.3): tap to place polygon/line
 * points in normalised image coordinates, close shapes, and save. The stop-line and signal ROI are
 * image-space (relative to the fixed camera), which is what the FSMs test against.
 */
@HiltViewModel
class JunctionConfigViewModel @Inject constructor(
    private val cameraController: CameraController,
    private val jurisdictionRepository: JurisdictionRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(JunctionConfigState())
    val state: StateFlow<JunctionConfigState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val jid = settingsRepository.settings.first().jurisdictionId
            val existing = jid?.let { jurisdictionRepository.junctionGeometryFor(it) }
            if (existing != null) {
                _state.value = _state.value.copy(
                    stopLine = existing.stopLine,
                    signalRoi = existing.signalRoi,
                    laneBoundaries = existing.laneBoundaries,
                )
            }
        }
    }

    fun setPermission(granted: Boolean) { _state.value = _state.value.copy(hasCameraPermission = granted) }

    fun bindCamera(owner: LifecycleOwner, surface: Preview.SurfaceProvider) {
        if (!_state.value.hasCameraPermission) return
        viewModelScope.launch { cameraController.bind(owner, surface) }
    }

    fun setMode(mode: ConfigMode) { _state.value = _state.value.copy(mode = mode, currentPoints = emptyList()) }

    fun addPoint(nx: Float, ny: Float) {
        _state.value = _state.value.copy(
            currentPoints = _state.value.currentPoints + NormPoint(nx.coerceIn(0f, 1f), ny.coerceIn(0f, 1f)),
            saved = false,
        )
    }

    fun undo() {
        _state.value = _state.value.copy(currentPoints = _state.value.currentPoints.dropLast(1))
    }

    /** Commit the in-progress points to the active element. */
    fun closeShape() {
        val s = _state.value
        val pts = s.currentPoints
        _state.value = when (s.mode) {
            ConfigMode.STOP_LINE -> if (pts.size >= 3) s.copy(stopLine = Polygon(pts), currentPoints = emptyList()) else s
            ConfigMode.SIGNAL_ROI -> if (pts.size >= 3) s.copy(signalRoi = Polygon(pts), currentPoints = emptyList()) else s
            ConfigMode.LANE -> if (pts.size >= 2) s.copy(laneBoundaries = s.laneBoundaries + LaneBoundary(pts), currentPoints = emptyList()) else s
        }
    }

    fun clearAll() {
        _state.value = _state.value.copy(
            currentPoints = emptyList(), stopLine = null, signalRoi = null, laneBoundaries = emptyList(), saved = false,
        )
    }

    fun save() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val jurisdictionId = settings.jurisdictionId ?: createDefaultJurisdiction(settings.officerId)
            val geometry = JunctionGeometry(
                stopLine = _state.value.stopLine,
                signalRoi = _state.value.signalRoi,
                laneBoundaries = _state.value.laneBoundaries,
            )
            jurisdictionRepository.saveJunctionGeometry(
                id = "${jurisdictionId}_junction",
                jurisdictionId = jurisdictionId,
                name = "Primary junction",
                geometry = geometry,
            )
            _state.value = _state.value.copy(saved = true)
            Timber.i("Junction geometry saved for $jurisdictionId")
        }
    }

    private suspend fun createDefaultJurisdiction(officerId: String): String {
        val id = UUID.randomUUID().toString()
        jurisdictionRepository.upsert(JurisdictionEntity(id, "Default", officerId, null, "{}"))
        settingsRepository.setJurisdiction(id)
        return id
    }

    override fun onCleared() {
        cameraController.unbind()
        super.onCleared()
    }
}
