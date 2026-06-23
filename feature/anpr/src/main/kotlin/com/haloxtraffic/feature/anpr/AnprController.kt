package com.haloxtraffic.feature.anpr

import android.graphics.Bitmap
import com.haloxtraffic.core.model.DetectionConfig
import com.haloxtraffic.core.model.DeviceTier
import com.haloxtraffic.core.model.IoDispatcher
import com.haloxtraffic.core.model.PlateColor
import com.haloxtraffic.core.model.PlateRead
import com.haloxtraffic.feature.detection.model.ModelKind
import com.haloxtraffic.feature.detection.model.ModelProvisioner
import com.haloxtraffic.feature.detection.model.ModelRegistry
import com.haloxtraffic.feature.detection.model.ProvisionState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Lifecycle/health of the ANPR recognizer, surfaced in the HUD. */
enum class OcrStatus { IDLE, PROVISIONING, LOADING, RUNNING, NO_MODEL, ERROR }

/**
 * Stage 4 orchestrator (§7), invoked off the hot path when a violation COMMITs. Provisions + loads the
 * PP-OCRv5 recognizer, then for a set of candidate plate crops: ranks by sharpness (best-frame),
 * recognises the top-N, classifies plate colour, and fuses to a validated [PlateRead] via [AnprPipeline].
 *
 * Never fabricates a plate: with no model (placeholder URL) it stays [OcrStatus.NO_MODEL] and returns
 * [PlateRead.unreadable]; an unvalidatable read is surfaced as uncertain, not as a clean result.
 */
@Singleton
class AnprController @Inject constructor(
    private val ocrEngine: PlateOcrEngine,
    private val provisioner: ModelProvisioner,
    private val registry: ModelRegistry,
    private val pipeline: AnprPipeline,
    private val colorClassifier: PlateColorClassifier,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val _status = MutableStateFlow(OcrStatus.IDLE)
    val status: StateFlow<OcrStatus> = _status.asStateFlow()

    /** Load the bundled OCR recognizer (PP-OCRv5, shipped in assets). Returns true when ready. */
    suspend fun start(tier: DeviceTier): Boolean = withContext(ioDispatcher) {
        _status.value = OcrStatus.LOADING
        runCatching { ocrEngine.init() }
            .onSuccess { _status.value = OcrStatus.RUNNING }
            .onFailure { Timber.e(it, "OCR init failed"); _status.value = OcrStatus.ERROR }
            .isSuccess
    }

    /**
     * Recognise the plate from candidate [crops] (newest-first from the detector buffer). Ranks by
     * sharpness, OCRs the top-N, classifies colour from the sharpest, and returns a fused [PlateRead].
     * Recycles the crops when done.
     */
    suspend fun recognizePlate(crops: List<Bitmap>): PlateRead = withContext(ioDispatcher) {
        if (crops.isEmpty()) return@withContext PlateRead.unreadable()
        if (!ocrEngine.isReady()) {
            crops.forEach { it.recycle() }
            return@withContext PlateRead.unreadable()
        }
        try {
            val ranked = crops.sortedByDescending { ImageOps.sharpness(it) }
            val best = ranked.take(TOP_N)
            val color = runCatching { colorClassifier.classify(best.first()) }.getOrDefault(PlateColor.UNKNOWN)
            val reads = best.mapNotNull { crop -> runCatching { ocrEngine.recognize(crop) }.getOrNull() }
            pipeline.resolve(reads, color)
        } catch (t: Throwable) {
            Timber.e(t, "ANPR recognition failed")
            PlateRead.unreadable()
        } finally {
            crops.forEach { it.recycle() }
        }
    }

    companion object {
        const val TOP_N = 5
    }
}
