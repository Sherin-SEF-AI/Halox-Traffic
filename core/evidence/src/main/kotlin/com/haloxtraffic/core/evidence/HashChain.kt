package com.haloxtraffic.core.evidence

import javax.inject.Inject
import javax.inject.Singleton

/** One link in the tamper-evident evidence chain (§8). */
data class ChainLink(
    val id: String,
    /** Hash of this package's canonical content (independent of chain position). */
    val contentHash: String,
    /** The previous link's [linkHash], or null for the genesis link. */
    val prevHash: String?,
    /** Hash binding contentHash + prevHash — what the next link points back to. */
    val linkHash: String,
)

/** Result of walking the chain in seal order. */
sealed interface ChainVerification {
    data object Valid : ChainVerification
    data class Broken(val atId: String, val index: Int, val reason: String) : ChainVerification
}

/**
 * Append-only hash-chain over sealed evidence packages (§8/§14). Each link binds its content hash to
 * the previous link's hash, so any silent insertion, deletion, or reordering breaks the chain and is
 * detectable by [verify]. Pure and fully unit-testable.
 */
@Singleton
class HashChain @Inject constructor(
    private val hasher: Hasher,
) {
    /** Compute the link hash for a new package given the previous link hash. */
    fun link(id: String, contentHash: String, prevHash: String?): ChainLink {
        val linkHash = hasher.sha256("$contentHash|${prevHash ?: GENESIS}")
        return ChainLink(id, contentHash, prevHash, linkHash)
    }

    /**
     * Walk [links] in order and confirm each correctly references the previous link and that each
     * link hash recomputes from its content. Returns the first break found, or [ChainVerification.Valid].
     */
    fun verify(links: List<ChainLink>): ChainVerification {
        var expectedPrev: String? = null
        links.forEachIndexed { index, link ->
            if (link.prevHash != expectedPrev) {
                return ChainVerification.Broken(link.id, index, "prevHash mismatch (sequence altered)")
            }
            val recomputed = hasher.sha256("${link.contentHash}|${link.prevHash ?: GENESIS}")
            if (recomputed != link.linkHash) {
                return ChainVerification.Broken(link.id, index, "linkHash mismatch (content altered)")
            }
            expectedPrev = link.linkHash
        }
        return ChainVerification.Valid
    }

    companion object {
        const val GENESIS = "GENESIS"
    }
}
