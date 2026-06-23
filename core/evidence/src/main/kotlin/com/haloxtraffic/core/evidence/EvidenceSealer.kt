package com.haloxtraffic.core.evidence

import com.haloxtraffic.core.model.TimeTrust
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything needed to seal one violation into a tamper-evident package (§8). Media is referenced by
 * path; the sealer hashes file contents (not paths) so moving files doesn't invalidate the seal.
 */
data class EvidenceInput(
    val packageId: String,
    val caseId: String,
    val clip: File?,
    val stills: List<File>,
    val plateCrops: List<File>,
    /** Canonical metadata bound into the hash: plate, geo, time, violation type, model versions, etc. */
    val metadata: Map<String, String>,
    val timeTrust: TimeTrust,
)

/** The immutable result of sealing — persisted to the sealed store and chained. */
data class SealedEvidence(
    val packageId: String,
    val caseId: String,
    val contentHash: String,
    val prevHash: String?,
    val linkHash: String,
    val signature: String,
    val sealedAtMs: Long,
    val timeTrust: TimeTrust,
)

/**
 * Seals evidence: hash content → chain to the previous package → sign. Implementations must be
 * deterministic given the same input + prevHash so the seal is reproducible and verifiable.
 */
interface EvidenceSealer {
    fun seal(input: EvidenceInput, prevLinkHash: String?, sealedAtMs: Long): SealedEvidence
    fun verify(sealed: SealedEvidence): Boolean
}

/**
 * Default sealer wiring [Hasher] + [HashChain] + [KeystoreSigner]. Real and unit-testable now; Phase 5
 * feeds it real clip/still/crop files and persists the result to the immutable sealed store.
 */
@Singleton
class DefaultEvidenceSealer @Inject constructor(
    private val hasher: Hasher,
    private val hashChain: HashChain,
    private val signer: KeystoreSigner,
) : EvidenceSealer {

    override fun seal(input: EvidenceInput, prevLinkHash: String?, sealedAtMs: Long): SealedEvidence {
        // 1. Hash each media file's content; missing files contribute an explicit marker (never silent).
        val mediaDigests = buildMap {
            input.clip?.let { put("clip", fileDigest(it)) }
            input.stills.forEachIndexed { i, f -> put("still_$i", fileDigest(f)) }
            input.plateCrops.forEachIndexed { i, f -> put("crop_$i", fileDigest(f)) }
        }

        // 2. Canonical content hash over media digests + metadata + sealedAt.
        val contentHash = hasher.canonicalHash(
            input.metadata + mediaDigests + mapOf(
                "packageId" to input.packageId,
                "caseId" to input.caseId,
                "sealedAt" to sealedAtMs.toString(),
                "timeTrust" to input.timeTrust.name,
            ),
        )

        // 3. Chain + 4. sign.
        val chainLink = hashChain.link(input.packageId, contentHash, prevLinkHash)
        val signature = signer.sign(chainLink.linkHash)

        return SealedEvidence(
            packageId = input.packageId,
            caseId = input.caseId,
            contentHash = contentHash,
            prevHash = prevLinkHash,
            linkHash = chainLink.linkHash,
            signature = signature,
            sealedAtMs = sealedAtMs,
            timeTrust = input.timeTrust,
        ).also { Timber.i("Sealed evidence ${it.packageId} (chain=${it.linkHash.take(12)}…)") }
    }

    override fun verify(sealed: SealedEvidence): Boolean {
        val recomputedLink = hashChain.link(sealed.packageId, sealed.contentHash, sealed.prevHash)
        if (recomputedLink.linkHash != sealed.linkHash) return false
        return signer.verify(sealed.linkHash, sealed.signature)
    }

    private fun fileDigest(file: File): String =
        if (file.exists()) hasher.sha256(file) else "MISSING:${file.name}"
}
