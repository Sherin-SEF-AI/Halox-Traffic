package com.haloxtraffic.feature.capture

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.camera.core.Preview
import com.haloxtraffic.core.data.entity.SessionEntity
import com.haloxtraffic.core.data.repository.CaseDraft
import com.haloxtraffic.core.data.repository.JurisdictionRepository
import com.haloxtraffic.core.data.repository.SealingRepository
import com.haloxtraffic.core.data.repository.SessionRepository
import com.haloxtraffic.core.data.settings.SettingsRepository
import com.haloxtraffic.core.evidence.SealedStore
import com.haloxtraffic.core.model.DetectionClass
import com.haloxtraffic.core.model.JunctionGeometry
import com.haloxtraffic.core.model.ViolationType
import com.haloxtraffic.core.model.DeviceProfile
import com.haloxtraffic.core.model.DeviceTier
import com.haloxtraffic.core.model.GeoFix
import com.haloxtraffic.core.model.MountMode
import com.haloxtraffic.core.model.TimeTrust
import com.haloxtraffic.core.sensors.camera.CameraController
import com.haloxtraffic.core.sensors.camera.ClipEncoder
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
import com.haloxtraffic.feature.vlm.VlmController
import com.haloxtraffic.feature.vlm.VlmStatus
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
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
    val vlmStatus: VlmStatus = VlmStatus.DISABLED,
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
    private val sealingRepository: SealingRepository,
    private val sealedStore: SealedStore,
    private val jurisdictionRepository: JurisdictionRepository,
    private val vlmController: VlmController,
    private val clipEncoder: ClipEncoder,
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
        // On each COMMIT: ANPR (off hot path) → assemble media → seal to tamper-evident evidence.
        viewModelScope.launch {
            violationController.events.collect { event -> sealViolation(event) }
        }
        viewModelScope.launch {
            anprController.status.collect { s -> _state.value = _state.value.copy(ocrStatus = s) }
        }
    }

    private suspend fun sealViolation(event: ViolationEvent) {
        val sessionId = _state.value.sessionId ?: return
        val caseId = UUID.randomUUID().toString()

        // Plate crop for evidence: save the freshest candidate before ANPR recycles the bitmaps.
        val crops = detectionController.cropPlatesForTrack(event.track.box)
        val plateCropFile = crops.firstOrNull()?.let { sealedStore.saveCrop(caseId, it) }
        val plate = anprController.recognizePlate(crops) // recycles crops; the file is already written
        plateResults[event.trackId] = plate
        _state.value = _state.value.copy(
            lastPlate = plate.plate,
            lastPlateConfidence = plate.confidence,
            lastPlateValidated = plate.validated,
        )

        // Full-res context still (best fidelity). Failure is non-fatal — evidence flags it as absent.
        val stillFile = sealedStore.newStillFile(caseId)
        val stills = if (cameraController.captureStill(stillFile).isSuccess) listOf(stillFile) else emptyList()

        // Pre/post context clip from the detection ring buffer (off the main thread; fail-soft).
        val clipFrames = detectionController.recentClipFrames()
        val clipFile = if (clipFrames.isNotEmpty()) {
            withContext(Dispatchers.Default) { clipEncoder.encode(clipFrames, CLIP_FPS, sealedStore.newClipFile(caseId)) }
        } else {
            null
        }
        clipFrames.forEach { it.recycle() }

        val time = timeSource.now()
        val geo = _state.value.geo
        val session = sessionRepository.byId(sessionId)
        sealingRepository.sealCommit(
            CaseDraft(
                caseId = caseId,
                sessionId = sessionId,
                vehicleTrackId = event.trackId,
                type = event.type,
                severity = 1,
                tsMs = time.epochMs,
                lat = geo?.lat ?: 0.0,
                lon = geo?.lon ?: 0.0,
                accuracyM = geo?.accuracyM ?: Float.MAX_VALUE,
                heading = geo?.headingDeg,
                fsmTraceJson = traceToJson(event),
                plate = plate,
                vlmDescription = null,
                timeTrust = time.trust,
                clip = clipFile, // pre/post context clip (null if encoding unavailable on this device)
                stills = stills,
                plateCrops = listOfNotNull(plateCropFile),
                officerId = session?.officerId.orEmpty(),
                jurisdictionId = session?.jurisdictionId,
                modelVersionsJson = session?.modelVersionsJson ?: "{}",
            ),
        )

        // Stage 6: enrich the sealed case via the VLM, off the hot path (HIGH tier only).
        if (vlmController.isReady()) {
            enrichWithVlm(caseId, event.type, plate.plate, plate.uncertain, stillFile.takeIf { stills.isNotEmpty() }, plateCropFile)
        }
    }

    /** Async VLM pass: incident description + (only if OCR was uncertain) a plate candidate. */
    private fun enrichWithVlm(
        caseId: String,
        type: ViolationType,
        plate: String?,
        plateUncertain: Boolean,
        stillFile: java.io.File?,
        plateCropFile: java.io.File?,
    ) = viewModelScope.launch {
        val stillBmp = stillFile?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
        val cropBmp = plateCropFile?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
        val description = vlmController.describe(type, plate, stillBmp)
        val vlmPlate = if (plateUncertain && cropBmp != null) vlmController.readPlate(cropBmp) else null
        sealingRepository.attachVlm(caseId, description, vlmPlate)
        stillBmp?.recycle()
        cropBmp?.recycle()
    }

    private fun traceToJson(event: ViolationEvent): String =
        event.trace.joinToString(prefix = "[", postfix = "]") { e ->
            """{"frame":${e.frame},"state":"${e.state}","note":"${e.note.replace("\"", "'")}"}"""
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
            // VLM is HIGH-tier only and off the hot path; no-op on LOW/MID.
            vlmController.start(tier, adaptiveRuntime.config.value.vlmEnabled)
        }
        viewModelScope.launch {
            vlmController.status.collect { s -> _state.value = _state.value.copy(vlmStatus = s) }
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

            // Apply this jurisdiction's junction geometry + enable only the violations the detector can
            // actually feed (and that the geometry/mount support) — so nothing false-fires.
            val geometry = settings.jurisdictionId
                ?.let { jurisdictionRepository.junctionGeometryFor(it) } ?: JunctionGeometry.EMPTY
            // Capability ∩ geometry/mount, then minus whatever the user switched off in Settings.
            val enabled = enabledViolations(detectionController.supportedClasses, geometry, _state.value.mountMode)
                .filter { settings.isViolationEnabled(it) }.toSet()
            violationController.configure(geometry, enabled)
            // Skip the heavier plate/helmet models entirely when their violation is off (perf + user choice).
            detectionController.setExtraDetectors(
                plate = ViolationType.PLATE_MISSING_OR_OBSCURED in enabled,
                helmet = ViolationType.NO_HELMET in enabled,
            )

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

    /**
     * The violations to run = those whose detection cues the loaded model can actually produce, AND
     * whose geometry/mount preconditions hold (§6 positioning flag). With the bundled COCO detector this
     * resolves to Wrong-Way + Triple-Riding (+ Lane if boundaries are configured); helmet/plate/seatbelt/
     * phone/red-light light up only when a model that emits those classes is supplied.
     */
    private fun enabledViolations(
        supported: Set<DetectionClass>,
        geometry: JunctionGeometry,
        mountMode: MountMode,
    ): Set<ViolationType> = buildSet {
        val hasVehicle = supported.any { it.isVehicle }
        if (hasVehicle) add(ViolationType.WRONG_WAY)
        if (DetectionClass.MOTORCYCLE in supported && DetectionClass.PERSON in supported) {
            add(ViolationType.TRIPLE_RIDING)
        }
        if (DetectionClass.NO_HELMET in supported) add(ViolationType.NO_HELMET)
        if (DetectionClass.PLATE in supported) add(ViolationType.PLATE_MISSING_OR_OBSCURED)
        if (geometry.supportsRedLight && DetectionClass.TRAFFIC_LIGHT_RED in supported) {
            add(ViolationType.RED_LIGHT_JUMP)
        }
        if (geometry.supportsLane && hasVehicle) add(ViolationType.LANE_VIOLATION)
        if (mountMode != MountMode.HANDHELD && DetectionClass.SEATBELT in supported) add(ViolationType.NO_SEATBELT)
        if (mountMode != MountMode.HANDHELD && DetectionClass.PHONE in supported) add(ViolationType.PHONE_USE)
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
            vlmController.stop()
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
        vlmController.stop()
        super.onCleared()
    }

    private companion object {
        const val MODEL_VERSIONS_PLACEHOLDER = """{"detector":"pending","ocr":"pending","vlm":"pending"}"""
        const val CLIP_FPS = 6
    }
}
