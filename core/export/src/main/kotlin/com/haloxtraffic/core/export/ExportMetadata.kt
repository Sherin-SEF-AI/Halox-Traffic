package com.haloxtraffic.core.export

import kotlinx.serialization.Serializable

/**
 * Canonical metadata in an exported e-challan bundle (§8). Carries the sealed-package hash, chain
 * pointer, device signature and public key so the receiving enforcement system can re-verify integrity
 * independently of the app.
 */
@Serializable
data class ExportMetadata(
    val caseId: String,
    val type: String,
    val tsMs: Long,
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val plate: String?,
    val plateValidated: Boolean,
    val plateColor: String?,
    val status: String,
    val vlmDescription: String?,
    // Integrity
    val sha256: String?,
    val prevHash: String?,
    val signature: String?,
    val publicKeyB64: String?,
    val timeTrust: String?,
    val mediaFiles: List<String>,
)
