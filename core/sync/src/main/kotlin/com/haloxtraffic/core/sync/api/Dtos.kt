package com.haloxtraffic.core.sync.api

import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the §13 backend contract. All IDs are client-generated UUIDs so every endpoint is
 * idempotent: re-sending the same id is a no-op upsert, which makes the offline queue safe to retry.
 */

@Serializable
data class AuthRequest(val officerId: String, val jurisdictionId: String?, val deviceId: String)

@Serializable
data class AuthResponse(val token: String, val expiresAtMs: Long)

@Serializable
data class JunctionDto(
    val id: String,
    val jurisdictionId: String,
    val name: String,
    val stopLinePolygonJson: String?,
    val signalRoiPolygonJson: String?,
    val laneDirectionsJson: String,
)

@Serializable
data class ConfigResponse(
    val jurisdictionId: String,
    val name: String,
    val configJson: String,
    val junctions: List<JunctionDto>,
)

@Serializable
data class SessionManifestDto(
    val id: String,
    val officerId: String,
    val jurisdictionId: String?,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val deviceTier: String,
    val deviceMeta: String,
    val modelVersionsJson: String,
)

@Serializable
data class CaseDto(
    val id: String,
    val sessionId: String,
    val type: String,
    val tsMs: Long,
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val plateString: String?,
    val plateConfidence: Float?,
    val plateValidated: Boolean,
    /** Sealed package hash + chain pointer + signature, re-verified server-side on ingest. */
    val contentHash: String,
    val prevHash: String?,
    val signature: String,
)

@Serializable
data class CaseBatchRequest(val cases: List<CaseDto>)

@Serializable
data class UpsertResponse(val accepted: List<String>, val rejected: List<String> = emptyList())

@Serializable
data class VahanLookupRequest(val plate: String)

@Serializable
data class VahanLookupResponse(
    val plate: String,
    val found: Boolean,
    val ownerHint: String? = null,
    val vehicleClass: String? = null,
    val registeredState: String? = null,
)
