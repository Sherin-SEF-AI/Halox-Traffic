package com.haloxtraffic.core.evidence

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HashChainTest {

    private val hasher = Hasher()
    private val chain = HashChain(hasher)

    private fun build(contents: List<String>): List<ChainLink> {
        val links = mutableListOf<ChainLink>()
        var prev: String? = null
        contents.forEachIndexed { i, content ->
            val link = chain.link("pkg$i", hasher.sha256(content), prev)
            links += link
            prev = link.linkHash
        }
        return links
    }

    @Test fun `intact chain verifies`() {
        val links = build(listOf("a", "b", "c"))
        assertThat(chain.verify(links)).isEqualTo(ChainVerification.Valid)
    }

    @Test fun `deleting a middle link is detected`() {
        val links = build(listOf("a", "b", "c")).toMutableList()
        links.removeAt(1) // drop the middle package
        assertThat(chain.verify(links)).isInstanceOf(ChainVerification.Broken::class.java)
    }

    @Test fun `reordering links is detected`() {
        val links = build(listOf("a", "b", "c")).toMutableList()
        val tmp = links[1]; links[1] = links[2]; links[2] = tmp
        assertThat(chain.verify(links)).isInstanceOf(ChainVerification.Broken::class.java)
    }

    @Test fun `altering content breaks the link hash`() {
        val links = build(listOf("a", "b", "c")).toMutableList()
        // Tamper with content hash but keep the old linkHash → recompute mismatch.
        links[1] = links[1].copy(contentHash = hasher.sha256("TAMPERED"))
        val result = chain.verify(links)
        assertThat(result).isInstanceOf(ChainVerification.Broken::class.java)
        assertThat((result as ChainVerification.Broken).index).isEqualTo(1)
    }

    @Test fun `canonical hash is order-independent`() {
        val h1 = hasher.canonicalHash(mapOf("b" to "2", "a" to "1"))
        val h2 = hasher.canonicalHash(mapOf("a" to "1", "b" to "2"))
        assertThat(h1).isEqualTo(h2)
    }
}
