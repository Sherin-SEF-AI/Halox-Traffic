package com.haloxtraffic.feature.vlm

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemma 3n vision engine via the MediaPipe LLM Inference API (§4). Loads a `.task`/`.litertlm` model
 * with the image budget and selected backend, then runs each request in a fresh vision-enabled session.
 * Synchronous generation is fine here because the VLM runs off the hot path (Stage 6, on-demand).
 *
 * CONFIRM at build time: the `tasks-genai` version and this multimodal API surface (option/session
 * builders, `setEnableVisionModality`, `addImage`). All MediaPipe usage is isolated to this file so a
 * version bump only touches here.
 */
@Singleton
class MediaPipeVlmEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : VlmEngine {

    private var llm: LlmInference? = null

    override fun isReady(): Boolean = llm != null

    override fun init(modelFile: File, useGpu: Boolean) {
        require(modelFile.exists()) { "VLM model not provisioned: ${modelFile.name}" }
        close()
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(MAX_TOKENS)
            .setMaxNumImages(MAX_IMAGES_PER_SESSION)
            .setPreferredBackend(if (useGpu) LlmInference.Backend.GPU else LlmInference.Backend.CPU)
            .build()
        llm = LlmInference.createFromOptions(context, options)
        Timber.i("MediaPipe VLM ready (${modelFile.name}, gpu=$useGpu)")
    }

    override fun generate(prompt: String, image: Bitmap?): String? {
        val engine = llm ?: return null
        return runCatching {
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTopK(TOP_K)
                .setTemperature(TEMPERATURE)
                .setGraphOptions(GraphOptions.builder().setEnableVisionModality(image != null).build())
                .build()
            LlmInferenceSession.createFromOptions(engine, sessionOptions).use { session ->
                session.addQueryChunk(prompt)
                if (image != null) session.addImage(BitmapImageBuilder(image).build())
                session.generateResponse()?.trim()
            }
        }.onFailure { Timber.e(it, "VLM generation failed") }.getOrNull()
    }

    override fun close() {
        llm?.let { runCatching { it.close() } }
        llm = null
    }

    private companion object {
        const val MAX_TOKENS = 1024
        const val MAX_IMAGES_PER_SESSION = 1
        const val TOP_K = 40
        const val TEMPERATURE = 0.2f
    }
}
