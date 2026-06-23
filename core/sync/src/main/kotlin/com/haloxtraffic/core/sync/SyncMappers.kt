package com.haloxtraffic.core.sync

import com.haloxtraffic.core.data.entity.EvidencePackageEntity
import com.haloxtraffic.core.data.entity.SessionEntity
import com.haloxtraffic.core.data.entity.ViolationCaseEntity
import com.haloxtraffic.core.sync.api.CaseDto
import com.haloxtraffic.core.sync.api.SessionManifestDto

/** Entity → wire DTO mappers for the §13 sync contract. */
internal fun SessionEntity.toDto() = SessionManifestDto(
    id = id,
    officerId = officerId,
    jurisdictionId = jurisdictionId,
    startedAtMs = startedAt,
    endedAtMs = endedAt,
    deviceTier = deviceTier.name,
    deviceMeta = deviceMeta,
    modelVersionsJson = modelVersionsJson,
)

internal fun ViolationCaseEntity.toDto(pkg: EvidencePackageEntity?) = CaseDto(
    id = id,
    sessionId = sessionId,
    type = type.name,
    tsMs = ts,
    lat = lat,
    lon = lon,
    accuracyM = accuracyM,
    plateString = plateString,
    plateConfidence = plateConfidence,
    plateValidated = plateValidated,
    contentHash = pkg?.sha256.orEmpty(),
    prevHash = pkg?.prevHash,
    signature = pkg?.signature.orEmpty(),
)
