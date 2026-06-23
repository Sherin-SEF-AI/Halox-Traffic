package com.haloxtraffic.core.model

import kotlinx.serialization.Serializable

/**
 * Runtime inference budget for a [DeviceTier] (§2). Selected at startup from [forTier] and then
 * mutated by the adaptive runtime controller, which drops cadence → resolution → VLM before ever
 * degrading detection quality.
 *
 * @param detectorVariant model size key resolved by the model registry (e.g. "yolo26n", "yolo26s").
 * @param ocrVariant PaddleOCR PP-OCRv5 variant key ("tiny" ~1.5M, "small" ~7.7M, "medium" ~34.5M).
 * @param quantization numeric precision for detection + OCR.
 * @param targetFps inference cadence cap (frames/sec fed to the detector).
 * @param inputResolutionPx square detector input edge in pixels.
 * @param vlmEnabled whether the on-device VLM (Gemma 3n) may run on-demand.
 * @param delegateChain ordered delegate fallback; first that initialises wins.
 */
@Serializable
data class DetectionConfig(
    val detectorVariant: String,
    val ocrVariant: String,
    val quantization: Quantization,
    val targetFps: Int,
    val inputResolutionPx: Int,
    val vlmEnabled: Boolean,
    val delegateChain: List<InferenceDelegate>,
) {
    companion object {
        /** Default budget per tier, matching the §2 device-tiering table. */
        fun forTier(tier: DeviceTier): DetectionConfig = when (tier) {
            DeviceTier.LOW -> DetectionConfig(
                detectorVariant = "yolo26n",
                ocrVariant = "tiny",
                quantization = Quantization.INT8,
                targetFps = 5,
                inputResolutionPx = 480,
                vlmEnabled = false,
                delegateChain = listOf(InferenceDelegate.XNNPACK_CPU),
            )
            DeviceTier.MID -> DetectionConfig(
                detectorVariant = "yolo26s",
                ocrVariant = "small",
                quantization = Quantization.INT8,
                targetFps = 9,
                inputResolutionPx = 640,
                vlmEnabled = false,
                delegateChain = listOf(InferenceDelegate.NNAPI, InferenceDelegate.GPU, InferenceDelegate.XNNPACK_CPU),
            )
            DeviceTier.HIGH -> DetectionConfig(
                detectorVariant = "yolo26m",
                ocrVariant = "small",
                quantization = Quantization.FP16,
                targetFps = 13,
                inputResolutionPx = 960,
                vlmEnabled = true,
                delegateChain = listOf(InferenceDelegate.GPU, InferenceDelegate.NNAPI, InferenceDelegate.XNNPACK_CPU),
            )
        }
    }
}
