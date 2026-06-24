package com.haloxtraffic.feature.violations

import com.haloxtraffic.core.model.BoundingBox
import com.haloxtraffic.core.model.JunctionGeometry
import com.haloxtraffic.core.model.ViolationType
import com.haloxtraffic.feature.violations.tracking.MultiObjectTracker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** A currently-visible track in violation, for red overlay drawing. */
data class ActiveViolation(
    val trackId: Long,
    val box: BoundingBox,
    val types: List<ViolationType>,
)

/**
 * Stage 2+3 orchestrator (§3): tracks vehicles, builds observations, runs the FSM bank, and exposes
 * committed violations. Stateless to its caller beyond [onFrame]; runs on whatever thread the caller
 * uses (the camera analysis thread in practice). Holds an internal frame counter so callers don't have
 * to thread one through.
 */
@Singleton
class ViolationController @Inject constructor() {

    private var thresholds = ViolationThresholds()
    private var geometry = JunctionGeometry.EMPTY
    private var enabledTypes = ViolationEngine.DEFAULT_TYPES
    private var tracker = MultiObjectTracker()
    private var builder = ObservationBuilder(tracker, thresholds, geometry)
    private var engine = ViolationEngine(thresholds, enabledTypes)
    private var frame = 0L

    private val _active = MutableStateFlow<List<ActiveViolation>>(emptyList())
    /** Tracks currently visible AND in violation — for the live red overlay. */
    val active: StateFlow<List<ActiveViolation>> = _active.asStateFlow()

    private val _committedCount = MutableStateFlow(0)
    /** Distinct (track, type) violations committed this session. */
    val committedCount: StateFlow<Int> = _committedCount.asStateFlow()

    private val _events = MutableSharedFlow<ViolationEvent>(extraBufferCapacity = 16)
    /** Fires once per new COMMIT — drives the haptic/discrete cue. */
    val events: SharedFlow<ViolationEvent> = _events.asSharedFlow()

    /** Feed one frame's detections (boxes in upright-frame normalised coords). Returns new COMMITs. */
    fun onFrame(boxes: List<BoundingBox>): List<ViolationEvent> {
        val f = frame++
        val observations = builder.build(boxes, f)

        // Diagnostic: surface the helmet chain (detection -> head association -> moving) so a missing
        // No-Helmet commit can be traced to the exact stage that dropped it.
        observations.filter { it.helmetedHeads > 0 || it.unhelmetedHeads > 0 }.forEach {
            Timber.d("HelmetAssoc track=${it.track.trackId} moving=${it.moving} helmeted=${it.helmetedHeads} bare=${it.unhelmetedHeads}")
        }

        val newEvents = engine.process(observations)

        if (newEvents.isNotEmpty()) {
            _committedCount.value += newEvents.size
            newEvents.forEach {
                _events.tryEmit(it)
                Timber.i("VIOLATION ${it.type} track=${it.trackId} @frame=${it.frame}")
            }
        }

        // Refresh the live overlay: any visible track with committed violations.
        _active.value = observations.mapNotNull { obs ->
            val types = engine.committedTypesFor(obs.track.trackId)
            if (types.isEmpty()) null
            else ActiveViolation(obs.track.trackId, obs.track.box, types.toList())
        }
        return newEvents
    }

    /**
     * Apply a junction's geometry and the full set of violation types to run this session (§6). The
     * caller derives [enabledTypes] from both the junction geometry (positioning flag) and the detector's
     * actual capabilities, so violations whose cues the model can't produce never fire.
     */
    fun configure(geometry: JunctionGeometry, enabledTypes: Set<ViolationType>) {
        this.geometry = geometry
        this.enabledTypes = enabledTypes
        builder.geometry = geometry
        engine = ViolationEngine(thresholds, enabledTypes)
    }

    fun reset() {
        tracker = MultiObjectTracker()
        builder = ObservationBuilder(tracker, thresholds, geometry)
        engine = ViolationEngine(thresholds, enabledTypes)
        frame = 0L
        _active.value = emptyList()
        _committedCount.value = 0
    }
}
