package com.haloxtraffic.core.evidence

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SHA-256 hashing over canonicalised evidence bytes (§8). Pure and fully unit-testable. The package
 * builder feeds this a canonical byte representation (media digests + sorted metadata) so the same
 * package always hashes identically regardless of map iteration order.
 */
@Singleton
class Hasher @Inject constructor() {

    fun sha256(bytes: ByteArray): String = digest().run {
        update(bytes)
        digest().toHex()
    }

    fun sha256(text: String): String = sha256(text.toByteArray(Charsets.UTF_8))

    /** Streaming hash for large media so we never load a clip fully into memory. */
    fun sha256(file: File): String = file.inputStream().use { sha256(it) }

    fun sha256(stream: InputStream): String {
        val md = digest()
        val buf = ByteArray(BUFFER)
        while (true) {
            val read = stream.read(buf)
            if (read < 0) break
            md.update(buf, 0, read)
        }
        return md.digest().toHex()
    }

    /**
     * Canonical hash over an ordered set of named components. Keys are sorted so the digest is
     * order-independent; each component contributes "key:value\n". Used to seal the metadata block.
     */
    fun canonicalHash(components: Map<String, String>): String {
        val canonical = components.toSortedMap()
            .entries.joinToString("\n") { (k, v) -> "$k:$v" }
        return sha256(canonical)
    }

    private fun digest() = MessageDigest.getInstance("SHA-256")

    companion object {
        private const val BUFFER = 64 * 1024

        fun ByteArray.toHex(): String = buildString(size * 2) {
            for (b in this@toHex) {
                val i = b.toInt() and 0xFF
                append(HEX[i ushr 4])
                append(HEX[i and 0x0F])
            }
        }

        private const val HEX = "0123456789abcdef"
    }
}
