package com.haloxtraffic.core.model

import kotlinx.serialization.Serializable

/**
 * Snapshot of device capability produced at first launch / config change by the device profiler
 * (`:core:sensors`). The [tier] is derived from these signals and persisted with each [Session] so
 * evidence records which hardware class produced them.
 */
@Serializable
data class DeviceProfile(
    val totalRamMb: Long,
    val abis: List<String>,
    val socModel: String,
    val nnapiAvailable: Boolean,
    val gpuDelegateAvailable: Boolean,
    /** Thermal status at profiling time, normalised 0f (nominal) .. 1f (critical). */
    val thermalHeadroom: Float,
    val tier: DeviceTier,
) {
    /** Compact one-line description for the onboarding tier card and the [Session] record. */
    val summary: String
        get() = "$tier · ${totalRamMb}MB · $socModel · ${abis.firstOrNull() ?: "?"} · " +
            "gpu=${if (gpuDelegateAvailable) "y" else "n"} nnapi=${if (nnapiAvailable) "y" else "n"}"
}
