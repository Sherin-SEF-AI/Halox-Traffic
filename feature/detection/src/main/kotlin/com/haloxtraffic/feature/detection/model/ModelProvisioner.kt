package com.haloxtraffic.feature.detection.model

import android.content.Context
import com.haloxtraffic.core.model.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Provisioning state for one model, surfaced by the onboarding ProvisioningProgress component. */
sealed interface ProvisionState {
    val spec: ModelSpec

    data class Cached(override val spec: ModelSpec, val file: File) : ProvisionState
    data class Downloading(override val spec: ModelSpec, val progress: Float) : ProvisionState
    data class Verifying(override val spec: ModelSpec) : ProvisionState
    data class Ready(override val spec: ModelSpec, val file: File) : ProvisionState
    data class Failed(override val spec: ModelSpec, val reason: String) : ProvisionState
}

/**
 * Downloads, integrity-verifies (SHA-256), and caches model assets to an app-private dir (§12.1).
 * Already-verified files are reused. A hash mismatch fails closed — a tampered/partial model is never
 * loaded. Real implementation; only the asset URLs/hashes in [ModelRegistry] are placeholders.
 */
@Singleton
class ModelProvisioner @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val httpClient: OkHttpClient,
) {
    private val modelsDir: File by lazy { File(context.filesDir, "models").apply { mkdirs() } }

    fun resolvedFile(spec: ModelSpec): File = File(modelsDir, spec.fileName)

    /** True if a verified copy already exists locally. */
    fun isCached(spec: ModelSpec): Boolean {
        val f = resolvedFile(spec)
        return f.exists() && sha256(f).equals(spec.sha256, ignoreCase = true)
    }

    /** Provision a single asset, emitting progress. Resumes from cache when valid. */
    fun provision(spec: ModelSpec): Flow<ProvisionState> = flow {
        val target = resolvedFile(spec)
        if (isCached(spec)) {
            emit(ProvisionState.Ready(spec, target))
            return@flow
        }

        val request = Request.Builder().url(spec.url).build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(ProvisionState.Failed(spec, "HTTP ${response.code}"))
                    return@flow
                }
                val body = response.body ?: run {
                    emit(ProvisionState.Failed(spec, "empty body"))
                    return@flow
                }
                val total = body.contentLength().takeIf { it > 0 }
                val tmp = File(modelsDir, "${spec.fileName}.part")
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buf)
                            if (read < 0) break
                            output.write(buf, 0, read)
                            downloaded += read
                            if (total != null) emit(ProvisionState.Downloading(spec, downloaded.toFloat() / total))
                        }
                    }
                }

                emit(ProvisionState.Verifying(spec))
                val actual = sha256(tmp)
                if (!actual.equals(spec.sha256, ignoreCase = true)) {
                    tmp.delete()
                    emit(ProvisionState.Failed(spec, "hash mismatch (expected ${spec.sha256.take(8)}…, got ${actual.take(8)}…)"))
                    return@flow
                }
                if (!tmp.renameTo(target)) {
                    emit(ProvisionState.Failed(spec, "could not finalize file"))
                    return@flow
                }
                emit(ProvisionState.Ready(spec, target))
            }
        } catch (t: Throwable) {
            Timber.e(t, "Provisioning ${spec.fileName} failed")
            emit(ProvisionState.Failed(spec, t.message ?: "download error"))
        }
    }.flowOn(ioDispatcher)

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buf)
                if (read < 0) break
                md.update(buf, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
