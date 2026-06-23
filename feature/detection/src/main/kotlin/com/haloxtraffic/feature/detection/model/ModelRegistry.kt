package com.haloxtraffic.feature.detection.model

import com.haloxtraffic.core.model.DetectionClass
import com.haloxtraffic.core.model.DetectionConfig
import com.haloxtraffic.core.model.Quantization
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the model assets required for a given [DetectionConfig] (and thus device tier). URLs +
 * hashes are placeholders to be filled with your hosted, exported assets (§12.1). The detector class
 * order in [DetectorOutputLayout] MUST match your YOLO26 export.
 */
@Singleton
class ModelRegistry @Inject constructor() {

    /** Detector classes the violation FSMs depend on (§6); canonical order from [DetectionClass]. */
    val detectorClasses: List<String> = DetectionClass.labels

    fun specsFor(config: DetectionConfig): List<ModelSpec> = buildList {
        add(detectorSpec(config.detectorVariant, config.inputResolutionPx, config.quantization))
        add(ocrSpec(config.ocrVariant, config.quantization))
        if (config.vlmEnabled) add(vlmSpec())
    }

    private fun detectorSpec(variant: String, inputSize: Int, quant: Quantization) = ModelSpec(
        kind = ModelKind.DETECTOR,
        variant = variant,
        fileName = "$variant.tflite",
        url = "$BASE_URL/detector/$variant.tflite",
        sha256 = PLACEHOLDER_HASH,
        quantization = quant,
        inputSize = inputSize,
        detectorOutput = DetectorOutputLayout(
            // CONFIRM all of these from your export.
            numDetections = 300,
            attributesPerBox = 6, // [x, y, w, h, score, classId] — adjust to your export
            boxFormat = BoxFormat.XYWH,
            coordsNormalized = true,
            classNames = detectorClasses,
        ),
    )

    private fun ocrSpec(variant: String, quant: Quantization) = ModelSpec(
        kind = ModelKind.PLATE_OCR,
        variant = "ppocrv5-$variant",
        fileName = "ppocrv5-$variant.tflite",
        url = "$BASE_URL/ocr/ppocrv5-$variant.tflite",
        sha256 = PLACEHOLDER_HASH,
        quantization = quant,
        inputSize = 48, // PP-OCRv5 recognizer input height; confirm at export
    )

    private fun vlmSpec() = ModelSpec(
        kind = ModelKind.VLM,
        variant = "gemma-3n-e2b",
        fileName = "gemma-3n-e2b.task",
        url = "$BASE_URL/vlm/gemma-3n-e2b.task",
        sha256 = PLACEHOLDER_HASH,
        quantization = Quantization.INT8,
        inputSize = 0,
    )

    companion object {
        // TODO: point at your hosted, integrity-checked model assets.
        private const val BASE_URL = "https://models.haloxtraffic.example/v1"
        private const val PLACEHOLDER_HASH = "0000000000000000000000000000000000000000000000000000000000000000"
    }
}
