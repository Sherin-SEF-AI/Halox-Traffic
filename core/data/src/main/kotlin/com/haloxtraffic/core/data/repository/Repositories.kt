package com.haloxtraffic.core.data.repository

import com.haloxtraffic.core.data.dao.EvidencePackageDao
import com.haloxtraffic.core.data.dao.JunctionDao
import com.haloxtraffic.core.data.dao.JurisdictionDao
import com.haloxtraffic.core.data.dao.PlateAuditDao
import com.haloxtraffic.core.data.dao.SessionDao
import com.haloxtraffic.core.data.dao.ViolationCaseDao
import com.haloxtraffic.core.data.entity.EvidencePackageEntity
import com.haloxtraffic.core.data.entity.JunctionEntity
import com.haloxtraffic.core.data.entity.JurisdictionEntity
import com.haloxtraffic.core.data.entity.PlateAuditEntity
import com.haloxtraffic.core.data.entity.SessionEntity
import com.haloxtraffic.core.data.entity.ViolationCaseEntity
import com.haloxtraffic.core.model.JunctionGeometry
import com.haloxtraffic.core.model.LaneBoundary
import com.haloxtraffic.core.model.Polygon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
) {
    fun observeSessions(): Flow<List<SessionEntity>> = sessionDao.observeAll()
    suspend fun start(session: SessionEntity) = sessionDao.upsert(session)
    suspend fun end(id: String, endedAt: Long) = sessionDao.markEnded(id, endedAt)
    suspend fun byId(id: String) = sessionDao.byId(id)
}

@Singleton
class JurisdictionRepository @Inject constructor(
    private val jurisdictionDao: JurisdictionDao,
    private val junctionDao: JunctionDao,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun observeJurisdictions(): Flow<List<JurisdictionEntity>> = jurisdictionDao.observeAll()
    fun observeJunctions(jurisdictionId: String): Flow<List<JunctionEntity>> =
        junctionDao.observeForJurisdiction(jurisdictionId)
    suspend fun upsert(jurisdiction: JurisdictionEntity) = jurisdictionDao.upsert(jurisdiction)
    suspend fun upsert(junction: JunctionEntity) = junctionDao.upsert(junction)
    suspend fun byId(id: String) = jurisdictionDao.byId(id)

    /** Persist a junction's enforcement geometry (§6/§12.3) across the entity's JSON columns. */
    suspend fun saveJunctionGeometry(id: String, jurisdictionId: String, name: String, geometry: JunctionGeometry) {
        junctionDao.upsert(
            JunctionEntity(
                id = id,
                jurisdictionId = jurisdictionId,
                name = name,
                stopLinePolygonJson = geometry.stopLine?.let { json.encodeToString(it) },
                signalRoiPolygonJson = geometry.signalRoi?.let { json.encodeToString(it) },
                laneDirectionsJson = json.encodeToString(
                    LaneConfig(geometry.laneBoundaries, geometry.defaultLaneDirectionDeg),
                ),
            ),
        )
    }

    /** Geometry of the first configured junction for a jurisdiction, or null. */
    suspend fun junctionGeometryFor(jurisdictionId: String): JunctionGeometry? =
        observeJunctions(jurisdictionId).first().firstOrNull()?.let { toGeometry(it) }

    private fun toGeometry(e: JunctionEntity): JunctionGeometry {
        val lanes = runCatching { json.decodeFromString<LaneConfig>(e.laneDirectionsJson) }.getOrDefault(LaneConfig())
        return JunctionGeometry(
            stopLine = e.stopLinePolygonJson?.let { runCatching { json.decodeFromString<Polygon>(it) }.getOrNull() },
            signalRoi = e.signalRoiPolygonJson?.let { runCatching { json.decodeFromString<Polygon>(it) }.getOrNull() },
            laneBoundaries = lanes.laneBoundaries,
            defaultLaneDirectionDeg = lanes.defaultLaneDirectionDeg,
        )
    }

    @Serializable
    private data class LaneConfig(
        val laneBoundaries: List<LaneBoundary> = emptyList(),
        val defaultLaneDirectionDeg: Float? = null,
    )
}

/**
 * Cases + their sealed evidence + the append-only plate audit trail. Plate corrections never
 * overwrite the original read — they are recorded as a new [PlateAuditEntity] and the case's
 * displayed plate is updated, with the original preserved in the audit.
 */
@Singleton
class CaseRepository @Inject constructor(
    private val caseDao: ViolationCaseDao,
    private val evidenceDao: EvidencePackageDao,
    private val plateAuditDao: PlateAuditDao,
) {
    fun observeCases(): Flow<List<ViolationCaseEntity>> = caseDao.observeAll()
    fun observeCase(id: String): Flow<ViolationCaseEntity?> = caseDao.observeById(id)
    suspend fun caseById(id: String): ViolationCaseEntity? = caseDao.byId(id)
    fun observeByPlate(plate: String): Flow<List<ViolationCaseEntity>> = caseDao.observeByPlate(plate)
    fun observeCount(sessionId: String): Flow<Int> = caseDao.countForSession(sessionId)
    fun observeAudit(caseId: String): Flow<List<PlateAuditEntity>> = plateAuditDao.observeForCase(caseId)

    suspend fun insertCase(case: ViolationCaseEntity) = caseDao.insert(case)
    suspend fun updateCase(case: ViolationCaseEntity) = caseDao.update(case)
    suspend fun evidenceFor(caseId: String): EvidencePackageEntity? = evidenceDao.forCase(caseId)
    suspend fun appendPlateAudit(audit: PlateAuditEntity) = plateAuditDao.insert(audit)
}
