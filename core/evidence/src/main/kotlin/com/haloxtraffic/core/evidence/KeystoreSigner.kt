package com.haloxtraffic.core.evidence

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import timber.log.Timber
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signs evidence-package hashes with an Android Keystore key (§8), binding each package to this device.
 * The key is hardware-backed (StrongBox / TEE) where available; key material never leaves the Keystore.
 * Signatures are ECDSA-P256 over SHA-256 of the package hash, Base64-encoded for storage/transport.
 */
@Singleton
class KeystoreSigner @Inject constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** Ensure the signing key exists; create it on first use. Idempotent. */
    fun ensureKey() {
        if (keyStore.containsAlias(KEY_ALIAS)) return
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        generator.initialize(spec)
        generator.generateKeyPair()
        Timber.i("Evidence signing key created (alias=$KEY_ALIAS)")
    }

    /** Sign [packageHash] (a hex SHA-256). Returns a Base64 signature. */
    fun sign(packageHash: String): String {
        ensureKey()
        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM).apply {
            initSign(entry.privateKey as PrivateKey)
            update(packageHash.toByteArray(Charsets.UTF_8))
        }
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    /** Verify a Base64 [signatureB64] over [packageHash] against this device's public key. */
    fun verify(packageHash: String, signatureB64: String): Boolean = runCatching {
        val cert = keyStore.getCertificate(KEY_ALIAS) ?: return false
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM).apply {
            initVerify(cert.publicKey as PublicKey)
            update(packageHash.toByteArray(Charsets.UTF_8))
        }
        signature.verify(Base64.getDecoder().decode(signatureB64))
    }.getOrElse {
        Timber.e(it, "Signature verification failed")
        false
    }

    /** Base64 X.509 public key, exported with evidence so a server can verify independently. */
    fun publicKeyB64(): String? = keyStore.getCertificate(KEY_ALIAS)
        ?.publicKey?.encoded?.let { Base64.getEncoder().encodeToString(it) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "haloxtraffic.evidence.signing.v1"
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }
}
