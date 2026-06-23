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
import kotlinx.coroutines.flow.Flow
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
    fun observeJurisdictions(): Flow<List<JurisdictionEntity>> = jurisdictionDao.observeAll()
    fun observeJunctions(jurisdictionId: String): Flow<List<JunctionEntity>> =
        junctionDao.observeForJurisdiction(jurisdictionId)
    suspend fun upsert(jurisdiction: JurisdictionEntity) = jurisdictionDao.upsert(jurisdiction)
    suspend fun upsert(junction: JunctionEntity) = junctionDao.upsert(junction)
    suspend fun byId(id: String) = jurisdictionDao.byId(id)
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
    fun observeByPlate(plate: String): Flow<List<ViolationCaseEntity>> = caseDao.observeByPlate(plate)
    fun observeCount(sessionId: String): Flow<Int> = caseDao.countForSession(sessionId)
    fun observeAudit(caseId: String): Flow<List<PlateAuditEntity>> = plateAuditDao.observeForCase(caseId)

    suspend fun insertCase(case: ViolationCaseEntity) = caseDao.insert(case)
    suspend fun updateCase(case: ViolationCaseEntity) = caseDao.update(case)
    suspend fun evidenceFor(caseId: String): EvidencePackageEntity? = evidenceDao.forCase(caseId)
    suspend fun appendPlateAudit(audit: PlateAuditEntity) = plateAuditDao.insert(audit)
}
