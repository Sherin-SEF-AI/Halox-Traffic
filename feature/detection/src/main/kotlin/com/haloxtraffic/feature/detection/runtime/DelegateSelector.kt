package com.haloxtraffic.feature.detection.runtime

import com.haloxtraffic.core.model.DetectionConfig
import com.haloxtraffic.core.model.InferenceDelegate

/**
 * Pure ordering of the delegate fallback chain (§2): GPU → NNAPI → XNNPACK/CPU. The detector tries
 * each in order and uses the first that initialises, never hard-failing on a delegate init error.
 * Extracted as pure logic so the precedence is unit-testable without a LiteRT runtime.
 */
object DelegateSelector {

    /** Canonical precedence; higher is preferred. */
    private val precedence = mapOf(
        InferenceDelegate.GPU to 3,
        InferenceDelegate.NNAPI to 2,
        InferenceDelegate.XNNPACK_CPU to 1,
    )

    /**
     * Order [config]'s delegate chain by precedence, always ending with a CPU fallback so init can
     * never run out of options.
     */
    fun chain(config: DetectionConfig): List<InferenceDelegate> {
        val ordered = config.delegateChain.sortedByDescending { precedence[it] ?: 0 }
        return if (InferenceDelegate.XNNPACK_CPU in ordered) ordered
        else ordered + InferenceDelegate.XNNPACK_CPU
    }
}
