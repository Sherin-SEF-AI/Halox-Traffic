package com.haloxtraffic.feature.vlm

import android.graphics.Bitmap
import com.haloxtraffic.core.model.DetectionConfig
import com.haloxtraffic.core.model.DeviceTier
import com.haloxtraffic.core.model.IoDispatcher
import com.haloxtraffic.core.model.ViolationType
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
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

enum class VlmStatus { DISABLED, PROVISIONING, LOADING, READY, NO_MODEL, ERROR }

/**
 * Stage 6 orchestrator (§3/§7E): the on-device VLM, HIGH-tier only and strictly off the hot path. It
 * verifies ambiguous violations, reads plates PaddleOCR couldn't, and writes a human-readable incident
 * description — never gating the live loop. Image use is capped per session (the directive's 10-image
 * cap). It never fabricates: an unavailable/uncertain answer returns null.
 */
@Singleton
class VlmController @Inject constructor(
    private val engine: VlmEngine,
    private val provisioner: ModelProvisioner,
    private val registry: ModelRegistry,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val _status = MutableStateFlow(VlmStatus.DISABLED)
    val status: StateFlow<VlmStatus> = _status.asStateFlow()

    private val imagesUsed = AtomicInteger(0)

    fun isReady(): Boolean = _status.value == VlmStatus.READY && engine.isReady()

    /**
     * Provision + load Gemma for [tier]. No-op (DISABLED) unless [vlmEnabled] and the tier is HIGH —
     * the VLM is an enhancement, never a dependency of the core loop.
     */
    suspend fun start(tier: DeviceTier, vlmEnabled: Boolean): Boolean = withContext(io) {
        imagesUsed.set(0)
        if (!vlmEnabled || tier != DeviceTier.HIGH) {
            _status.value = VlmStatus.DISABLED
            return@withContext false
        }
        val spec = registry.specsFor(DetectionConfig.forTier(tier)).firstOrNull { it.kind == ModelKind.VLM }
            ?: run { _status.value = VlmStatus.DISABLED; return@withContext false }

        _status.value = VlmStatus.PROVISIONING
        var file: java.io.File? = null
        provisioner.provision(spec).collect { state ->
            when (state) {
                is ProvisionState.Ready -> file = state.file
                is ProvisionState.Cached -> file = state.file
                is ProvisionState.Failed -> { Timber.w("VLM model unavailable: ${state.reason}"); _status.value = VlmStatus.NO_MODEL }
                else -> Unit
            }
        }
        val f = file ?: run {
            if (_status.value != VlmStatus.NO_MODEL) _status.value = VlmStatus.NO_MODEL
            return@withContext false
        }
        _status.value = VlmStatus.LOADING
        runCatching { engine.init(f, useGpu = true) }
            .onSuccess { _status.value = VlmStatus.READY }
            .onFailure { Timber.e(it, "VLM init failed"); _status.value = VlmStatus.ERROR }
            .isSuccess
    }

    /** Verify an ambiguous violation. Returns true/false, or null if unavailable/over budget. */
    suspend fun verifyViolation(type: ViolationType, image: Bitmap): Boolean? = ask(image) {
        val answer = engine.generate(
            "You are a traffic-enforcement assistant. Looking only at this image, is the violation " +
                "'${type.displayName}' clearly occurring? Answer strictly YES or NO.",
            image,
        )?.uppercase()
        when {
            answer == null -> null
            answer.startsWith("YES") -> true
            answer.startsWith("NO") -> false
            else -> null
        }
    }

    /** Read a hard/angled plate the OCR couldn't. Returns a canonical candidate or null (never fabricates). */
    suspend fun readPlate(crop: Bitmap): String? = ask(crop) {
        val raw = engine.generate(
            "Read the Indian vehicle number plate in this image. Reply with ONLY the plate characters " +
                "(letters and digits, no spaces), or UNKNOWN if unreadable.",
            crop,
        ) ?: return@ask null
        val canonical = raw.uppercase().filter { it.isLetterOrDigit() }
        if (canonical.isEmpty() || canonical.contains("UNKNOWN")) null else canonical
    }

    /** One-sentence factual incident description for the case file. */
    suspend fun describe(type: ViolationType, plate: String?, image: Bitmap?): String? = ask(image) {
        engine.generate(
            "Briefly describe this traffic violation ('${type.displayName}'" +
                (plate?.let { ", vehicle $it" } ?: "") +
                ") in one factual sentence for an incident report.",
            image,
        )
    }

    /** Gate a VLM call on readiness + the per-session image budget, consuming budget when an image is used. */
    private suspend fun <T> ask(image: Bitmap?, block: suspend () -> T?): T? = withContext(io) {
        if (!isReady()) return@withContext null
        if (image != null && imagesUsed.incrementAndGet() > MAX_IMAGES_PER_SESSION) {
            Timber.d("VLM image budget exhausted for this session")
            return@withContext null
        }
        block()
    }

    fun stop() {
        engine.close()
        imagesUsed.set(0)
        if (_status.value != VlmStatus.DISABLED) _status.value = VlmStatus.DISABLED
    }

    companion object {
        const val MAX_IMAGES_PER_SESSION = 10
    }
}
