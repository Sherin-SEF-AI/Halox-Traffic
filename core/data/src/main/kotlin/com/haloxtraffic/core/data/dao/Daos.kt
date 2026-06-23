package com.haloxtraffic.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.haloxtraffic.core.data.entity.EvidencePackageEntity
import com.haloxtraffic.core.data.entity.JunctionEntity
import com.haloxtraffic.core.data.entity.JurisdictionEntity
import com.haloxtraffic.core.data.entity.PlateAuditEntity
import com.haloxtraffic.core.data.entity.SessionEntity
import com.haloxtraffic.core.data.entity.SyncQueueItemEntity
import com.haloxtraffic.core.data.entity.VehicleEntity
import com.haloxtraffic.core.data.entity.ViolationCaseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JurisdictionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: JurisdictionEntity)

    @Query("SELECT * FROM jurisdiction ORDER BY name")
    fun observeAll(): Flow<List<JurisdictionEntity>>

    @Query("SELECT * FROM jurisdiction WHERE id = :id")
    suspend fun byId(id: String): JurisdictionEntity?
}

@Dao
interface JunctionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: JunctionEntity)

    @Query("SELECT * FROM junction WHERE jurisdictionId = :jurisdictionId")
    fun observeForJurisdiction(jurisdictionId: String): Flow<List<JunctionEntity>>
}

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SessionEntity)

    @Query("UPDATE session SET endedAt = :endedAt WHERE id = :id")
    suspend fun markEnded(id: String, endedAt: Long)

    @Query("SELECT * FROM session ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun byId(id: String): SessionEntity?
}

@Dao
interface VehicleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: VehicleEntity)

    @Query("SELECT * FROM vehicle WHERE sessionId = :sessionId")
    suspend fun forSession(sessionId: String): List<VehicleEntity>
}

@Dao
interface ViolationCaseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ViolationCaseEntity)

    /** Status/plate-correction updates only; evidence itself is never mutated. */
    @Update
    suspend fun update(entity: ViolationCaseEntity)

    @Query("SELECT * FROM violation_case ORDER BY ts DESC")
    fun observeAll(): Flow<List<ViolationCaseEntity>>

    @Query("SELECT * FROM violation_case WHERE id = :id")
    fun observeById(id: String): Flow<ViolationCaseEntity?>

    @Query("SELECT * FROM violation_case WHERE id = :id")
    suspend fun byId(id: String): ViolationCaseEntity?

    @Query("SELECT * FROM violation_case WHERE plateString = :plate ORDER BY ts DESC")
    fun observeByPlate(plate: String): Flow<List<ViolationCaseEntity>>

    @Query("SELECT COUNT(*) FROM violation_case WHERE sessionId = :sessionId")
    fun countForSession(sessionId: String): Flow<Int>
}

/** Evidence is immutable: insert + read only, no update/delete. */
@Dao
interface EvidencePackageDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: EvidencePackageEntity)

    @Query("SELECT * FROM evidence_package WHERE caseId = :caseId")
    suspend fun forCase(caseId: String): EvidencePackageEntity?

    /** The whole chain in seal order — for the hash-chain integrity self-check (§14). */
    @Query("SELECT * FROM evidence_package ORDER BY sealedAt ASC")
    suspend fun chainInSealOrder(): List<EvidencePackageEntity>

    /** Most recently sealed package — its recomputed link hash anchors the next package. */
    @Query("SELECT * FROM evidence_package ORDER BY sealedAt DESC LIMIT 1")
    suspend fun latest(): EvidencePackageEntity?

    @Query("SELECT sha256 FROM evidence_package ORDER BY sealedAt DESC LIMIT 1")
    suspend fun latestHash(): String?
}

/** Plate corrections are append-only: insert + read only. */
@Dao
interface PlateAuditDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PlateAuditEntity)

    @Query("SELECT * FROM plate_audit WHERE caseId = :caseId ORDER BY ts ASC")
    fun observeForCase(caseId: String): Flow<List<PlateAuditEntity>>
}

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueItemEntity)

    @Query("SELECT * FROM sync_queue_item WHERE syncedAt IS NULL ORDER BY createdAt ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<SyncQueueItemEntity>

    @Query("UPDATE sync_queue_item SET syncedAt = :ts WHERE id = :id")
    suspend fun markSynced(id: String, ts: Long)

    @Query("UPDATE sync_queue_item SET retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    suspend fun markRetry(id: String, error: String)

    @Query("SELECT COUNT(*) FROM sync_queue_item WHERE syncedAt IS NULL")
    fun observePendingCount(): Flow<Int>
}
