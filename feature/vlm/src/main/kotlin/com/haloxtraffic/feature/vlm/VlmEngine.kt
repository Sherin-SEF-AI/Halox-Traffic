package com.haloxtraffic.feature.vlm

import android.graphics.Bitmap
import java.io.File

/**
 * On-device vision-language engine contract (Stage 6, §3/§7E). One generate call = one prompt + an
 * optional image. Kept behind an interface so it is fakeable in tests and the orchestration
 * ([VlmController]) is unit-testable without the MediaPipe runtime.
 */
interface VlmEngine {
    fun isReady(): Boolean

    /** Load the Gemma model. [useGpu] selects the MediaPipe backend at runtime. */
    fun init(modelFile: File, useGpu: Boolean)

    /** Run one multimodal generation. Returns the model's text, or null on failure. */
    fun generate(prompt: String, image: Bitmap?): String?

    fun close()
}
