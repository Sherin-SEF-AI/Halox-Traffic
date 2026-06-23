package com.haloxtraffic.core.data.repository

import com.haloxtraffic.core.data.dao.EvidencePackageDao
import com.haloxtraffic.core.data.dao.PlateAuditDao
import com.haloxtraffic.core.data.dao.ViolationCaseDao
import com.haloxtraffic.core.data.entity.EvidencePackageEntity
import com.haloxtraffic.core.data.entity.PlateAuditEntity
import com.haloxtraffic.core.data.entity.ViolationCaseEntity
import com.haloxtraffic.core.evidence.ChainVerification
import com.haloxtraffic.core.evidence.EvidenceInput
import com.haloxtraffic.core.evidence.EvidenceSealer
import com.haloxtraffic.core.evidence.HashChain
import com.haloxtraffic.core.evidence.Signer
import com.haloxtraffic.core.model.CaseStatus
import com.haloxtraffic.core.model.IoDispatcher
import com.haloxtraffic.core.model.PlateRead
import com.haloxtraffic.core.model.TimeTrust
import com.haloxtraffic.core.model.ViolationType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Everything needed to seal one committed violation into evidence (§8). Media referenced by file. */
data class CaseDraft(
    val caseId: String,
    val sessionId: String,
    val vehicleTrackId: Long,
    val type: ViolationType,
    val severity: Int,
    val tsMs: Long,
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val heading: Float?,
    val fsmTraceJson: String,
    val plate: PlateRead?,
    val vlmDescription: String?,
    val timeTrust: TimeTrust,
    val clip: File?,
    val stills: List<File>,
    val plateCrops: List<File>,
    val officerId: String,
    val jurisdictionId: String?,
    val modelVersionsJson: String,
)

/** Result of the sealed-store integrity self-check (§14). */
data class IntegrityResult(val chain: ChainVerification, val allSignaturesValid: Boolean) {
    val ok: Boolean get() = chain is ChainVerification.Valid && allSignaturesValid
}

/**
 * Seals committed violations into tamper-evident evidence and persists them (§8). Each package is
 * hashed, chained to the previous package's link, and signed; the case + package + the *original*
 * plate read (append-only audit) are written together. Plate corrections never overwrite the original —
 * they append a new audit row and update only the case's displayed plate.
 */
@Singleton
class SealingRepository @Inject constructor(
    private val caseDao: ViolationCaseDao,
    private val evidenceDao: EvidencePackageDao,
    private val plateAuditDao: PlateAuditDao,
    private val sealer: EvidenceSealer,
    private val hashChain: HashChain,
    private val signer: Signer,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val json = Json { encodeDefaults = true }

    /** Seal + persist a committed violation. Returns the case id. */
    suspend fun sealCommit(draft: CaseDraft): String = withContext(io) {
        val packageId = UUID.randomUUID().toString()
        val now = draft.tsMs
        val prevLink = evidenceDao.latest()?.let { recomputeLink(it) }

        val input = EvidenceInput(
            packageId = packageId,
            caseId = draft.caseId,
            clip = draft.clip,
            stills = draft.stills,
            plateCrops = draft.plateCrops,
            metadata = metadataOf(draft),
            timeTrust = draft.timeTrust,
        )
        val sealed = sealer.seal(input, prevLink, now)

        caseDao.insert(
            ViolationCaseEntity(
                id = draft.caseId,
                sessionId = draft.sessionId,
                vehicleTrackId = draft.vehicleTrackId,
                type = draft.type,
                severity = draft.severity,
                ts = draft.tsMs,
                lat = draft.lat,
                lon = draft.lon,
                accuracyM = draft.accuracyM,
                heading = draft.heading,
                fsmTraceJson = draft.fsmTraceJson,
                plateString = draft.plate?.plate,
                plateConfidence = draft.plate?.confidence,
                plateValidated = draft.plate?.validated ?: false,
                plateColor = draft.plate?.color,
                vlmDescription = draft.vlmDescription,
                status = CaseStatus.OPEN,
                evidencePackageId = packageId,
            ),
        )
        evidenceDao.insert(
            EvidencePackageEntity(
                id = packageId,
                caseId = draft.caseId,
                clipPath = draft.clip?.absolutePath,
                stillPathsJson = json.encodeToString(draft.stills.map { it.absolutePath }),
                plateCropPathsJson = json.encodeToString(draft.plateCrops.map { it.absolutePath }),
                sha256 = sealed.contentHash,
                prevHash = sealed.prevHash,
                signature = sealed.signature,
                sealedAt = now,
                timeTrustFlag = draft.timeTrust,
            ),
        )
        // Append-only: record the original read so a later correction never erases it.
        plateAuditDao.insert(
            PlateAuditEntity(
                id = UUID.randomUUID().toString(),
                caseId = draft.caseId,
                originalRead = draft.plate?.plate,
                originalConfidence = draft.plate?.confidence,
                correctedRead = null,
                reviewerId = null,
                reason = "initial read",
                ts = now,
            ),
        )
        Timber.i("Sealed case ${draft.caseId} (${draft.type})")
        draft.caseId
    }

    /** Append a reviewer plate correction (audited) and update only the case's displayed plate. */
    suspend fun correctPlate(caseId: String, correctedRead: String, reviewerId: String, reason: String) =
        withContext(io) {
            plateAuditDao.insert(
                PlateAuditEntity(
                    id = UUID.randomUUID().toString(),
                    caseId = caseId,
                    originalRead = caseDao.byId(caseId)?.plateString,
                    originalConfidence = caseDao.byId(caseId)?.plateConfidence,
                    correctedRead = correctedRead,
                    reviewerId = reviewerId,
                    reason = reason,
                    ts = System.currentTimeMillis(),
                ),
            )
            caseDao.byId(caseId)?.let {
                caseDao.update(it.copy(plateString = correctedRead, plateValidated = true))
            }
        }

    suspend fun setStatus(caseId: String, status: CaseStatus) = withContext(io) {
        caseDao.byId(caseId)?.let { caseDao.update(it.copy(status = status)) }
    }

    /** Verify one case's package: recompute its link hash + check the device signature. */
    suspend fun verifyCase(caseId: String): Boolean = withContext(io) {
        val pkg = evidenceDao.forCase(caseId) ?: return@withContext false
        val link = hashChain.link(pkg.id, pkg.sha256, pkg.prevHash)
        signer.verify(link.linkHash, pkg.signature)
    }

    /** Walk the full chain in seal order + verify every signature (§14 integrity self-check). */
    suspend fun verifyIntegrity(): IntegrityResult = withContext(io) {
        val pkgs = evidenceDao.chainInSealOrder()
        val links = pkgs.map { hashChain.link(it.id, it.sha256, it.prevHash) }
        val chain = hashChain.verify(links)
        val signaturesValid = pkgs.zip(links).all { (pkg, link) -> signer.verify(link.linkHash, pkg.signature) }
        IntegrityResult(chain, signaturesValid)
    }

    private fun recomputeLink(e: EvidencePackageEntity): String =
        hashChain.link(e.id, e.sha256, e.prevHash).linkHash

    private fun metadataOf(d: CaseDraft): Map<String, String> = buildMap {
        put("type", d.type.name)
        put("ts", d.tsMs.toString())
        put("lat", d.lat.toString())
        put("lon", d.lon.toString())
        put("accuracyM", d.accuracyM.toString())
        put("officerId", d.officerId)
        d.jurisdictionId?.let { put("jurisdictionId", it) }
        put("modelVersions", d.modelVersionsJson)
        put("vehicleTrackId", d.vehicleTrackId.toString())
        d.plate?.plate?.let { put("plate", it) }
        d.plate?.let { put("plateValidated", it.validated.toString()) }
    }
}
