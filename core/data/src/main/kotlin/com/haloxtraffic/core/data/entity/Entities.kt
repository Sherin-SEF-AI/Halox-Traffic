package com.haloxtraffic.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.haloxtraffic.core.model.CaseStatus
import com.haloxtraffic.core.model.DeviceTier
import com.haloxtraffic.core.model.MountMode
import com.haloxtraffic.core.model.PlateColor
import com.haloxtraffic.core.model.TimeTrust
import com.haloxtraffic.core.model.VehicleClass
import com.haloxtraffic.core.model.ViolationType

/**
 * Room entities (§9). Primary keys are client-generated UUID strings so sync is idempotent end to end
 * (the same id is used locally and on the server). All foreign keys + timestamps + plateString are
 * indexed (see annotations) for the case/map query paths.
 */

@Entity(tableName = "jurisdiction")
data class JurisdictionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val officerId: String,
    /** Default expected lane direction in degrees (for wrong-way), or null if unset. */
    val defaultLaneDirectionDeg: Float?,
    val configJson: String,
)

@Entity(
    tableName = "junction",
    foreignKeys = [
        ForeignKey(
            entity = JurisdictionEntity::class,
            parentColumns = ["id"],
            childColumns = ["jurisdictionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("jurisdictionId")],
)
data class JunctionEntity(
    @PrimaryKey val id: String,
    val jurisdictionId: String,
    val name: String,
    /** Normalised polygon JSON, or null until configured on the map. */
    val stopLinePolygonJson: String?,
    val signalRoiPolygonJson: String?,
    /** Per-lane expected directions JSON. */
    val laneDirectionsJson: String,
)

@Entity(
    tableName = "session",
    indices = [Index("jurisdictionId"), Index("startedAt")],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val jurisdictionId: String?,
    val officerId: String,
    val mountMode: MountMode,
    val startedAt: Long,
    val endedAt: Long?,
    val deviceTier: DeviceTier,
    /** Human-readable device profile summary. */
    val deviceMeta: String,
    /** Detector/OCR/VLM model versions JSON, bound into evidence. */
    val modelVersionsJson: String,
)

@Entity(
    tableName = "vehicle",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index(value = ["sessionId", "trackId"], unique = true)],
)
data class VehicleEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val trackId: Long,
    val vehicleClass: VehicleClass,
    val plateColor: PlateColor?,
    val firstSeenTs: Long,
    val lastSeenTs: Long,
)

@Entity(
    tableName = "violation_case",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("ts"), Index("plateString"), Index("type"), Index("status")],
)
data class ViolationCaseEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val vehicleTrackId: Long,
    val type: ViolationType,
    val severity: Int,
    val ts: Long,
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val heading: Float?,
    /** FSM decision trace JSON — the auditable "why" (criteria met, over how many frames). */
    val fsmTraceJson: String,
    val plateString: String?,
    val plateConfidence: Float?,
    val plateValidated: Boolean,
    val plateColor: PlateColor?,
    val vlmDescription: String?,
    val status: CaseStatus,
    val evidencePackageId: String?,
)

@Entity(
    tableName = "evidence_package",
    foreignKeys = [
        ForeignKey(
            entity = ViolationCaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["caseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["caseId"], unique = true), Index("sealedAt")],
)
data class EvidencePackageEntity(
    @PrimaryKey val id: String,
    val caseId: String,
    val clipPath: String?,
    /** JSON list of full-frame still paths. */
    val stillPathsJson: String,
    val plateCropPathsJson: String,
    val sha256: String,
    /** Previous package's hash — the tamper-evident chain link. */
    val prevHash: String?,
    val signature: String,
    val sealedAt: Long,
    val timeTrustFlag: TimeTrust,
)

@Entity(
    tableName = "plate_audit",
    foreignKeys = [
        ForeignKey(
            entity = ViolationCaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["caseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("caseId")],
)
data class PlateAuditEntity(
    @PrimaryKey val id: String,
    val caseId: String,
    val originalRead: String?,
    val originalConfidence: Float?,
    val correctedRead: String?,
    val reviewerId: String?,
    val reason: String?,
    val ts: Long,
)

@Entity(
    tableName = "sync_queue_item",
    indices = [Index("syncedAt"), Index(value = ["entityType", "entityId"])],
)
data class SyncQueueItemEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val op: String,
    val payloadJson: String,
    val createdAt: Long,
    val syncedAt: Long?,
    val retryCount: Int,
    val lastError: String?,
)
