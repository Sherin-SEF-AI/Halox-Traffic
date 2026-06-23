package com.haloxtraffic.core.sensors.profile

import com.haloxtraffic.core.model.DetectionConfig
import com.haloxtraffic.core.model.DeviceTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the live [DetectionConfig] and adapts it under load (§2). The back-off order is fixed:
 * drop cadence → drop input resolution → disable VLM, all *before* degrading detection quality.
 * Recovery walks back up when headroom returns. Detection (Phase 2) feeds real per-stage latency in;
 * for now the stepping logic + state holder are wired and unit-testable.
 */
@Singleton
class AdaptiveRuntimeController @Inject constructor() {

    private val _config = MutableStateFlow(DetectionConfig.forTier(DeviceTier.LOW))
    val config: StateFlow<DetectionConfig> = _config.asStateFlow()

    private val _degraded = MutableStateFlow(false)
    /** True when the runtime is running below the tier's nominal budget — surfaced as amber in the HUD. */
    val degraded: StateFlow<Boolean> = _degraded.asStateFlow()

    private var baseline: DetectionConfig = _config.value

    /** Reset to a tier's nominal budget (called once the device is profiled / overridden). */
    fun reset(tier: DeviceTier) {
        baseline = DetectionConfig.forTier(tier)
        _config.value = baseline
        _degraded.value = false
    }

    /**
     * Report observed average inference latency and thermal pressure. Steps the budget down when over
     * budget / hot, up when comfortably under. Pure-ish: only mutates the held config.
     *
     * @param avgLatencyMs measured average per-frame inference latency.
     * @param thermalHeadroom 0f (nominal) .. 1f (critical).
     */
    fun report(avgLatencyMs: Long, thermalHeadroom: Float) {
        val budgetMs = 1000L / baseline.targetFps
        val overBudget = avgLatencyMs > budgetMs * OVER_BUDGET_FACTOR
        val hot = thermalHeadroom >= THERMAL_DEGRADE_AT

        if (overBudget || hot) {
            stepDown()
        } else if (avgLatencyMs < budgetMs * RECOVER_FACTOR && thermalHeadroom < THERMAL_RECOVER_AT) {
            stepUp()
        }
    }

    private fun stepDown() {
        val c = _config.value
        val next = when {
            c.targetFps > MIN_FPS -> c.copy(targetFps = (c.targetFps - FPS_STEP).coerceAtLeast(MIN_FPS))
            c.inputResolutionPx > MIN_RES -> c.copy(inputResolutionPx = (c.inputResolutionPx - RES_STEP).coerceAtLeast(MIN_RES))
            c.vlmEnabled -> c.copy(vlmEnabled = false)
            else -> c // already at the floor — detection quality is never sacrificed
        }
        if (next != c) {
            Timber.w("Adaptive back-off: fps=${next.targetFps} res=${next.inputResolutionPx} vlm=${next.vlmEnabled}")
            _config.value = next
            _degraded.value = next != baseline
        }
    }

    private fun stepUp() {
        val c = _config.value
        if (c == baseline) return
        val next = when {
            !c.vlmEnabled && baseline.vlmEnabled -> c.copy(vlmEnabled = true)
            c.inputResolutionPx < baseline.inputResolutionPx ->
                c.copy(inputResolutionPx = (c.inputResolutionPx + RES_STEP).coerceAtMost(baseline.inputResolutionPx))
            c.targetFps < baseline.targetFps ->
                c.copy(targetFps = (c.targetFps + FPS_STEP).coerceAtMost(baseline.targetFps))
            else -> baseline
        }
        _config.value = next
        _degraded.value = next != baseline
    }

    companion object {
        const val OVER_BUDGET_FACTOR = 1.3
        const val RECOVER_FACTOR = 0.7
        const val THERMAL_DEGRADE_AT = 0.6f
        const val THERMAL_RECOVER_AT = 0.3f
        const val MIN_FPS = 3
        const val FPS_STEP = 2
        const val MIN_RES = 384
        const val RES_STEP = 128
    }
}
