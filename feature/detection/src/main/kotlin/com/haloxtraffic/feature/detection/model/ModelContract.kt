package com.haloxtraffic.feature.detection.model

import com.haloxtraffic.core.model.Quantization

/** Which on-device model an asset provides. */
enum class ModelKind { DETECTOR, PLATE_OCR, PLATE_DETECTOR, VLM }

/**
 * Provenance + integrity + I/O contract for one downloadable model asset (§2/§12.1). Assets are NOT
 * committed; [url] + [sha256] let the provisioner fetch and verify them at first launch. The I/O
 * fields document the tensor layout the runtime decodes against — confirm them from your own export
 * (`yolo export ... format=tflite int8=True`) and update here.
 */
data class ModelSpec(
    val kind: ModelKind,
    /** Registry key, e.g. "yolo26n", "yolo26s", "ppocrv5-tiny". */
    val variant: String,
    val fileName: String,
    val url: String,
    val sha256: String,
    val quantization: Quantization,
    /** Square input edge in px the model expects (detector). */
    val inputSize: Int,
    /**
     * Output tensor description for the decoder. For NMS-free YOLO26 this is the final boxes tensor
     * (no NMS pass) — confirm the exact shape/order from your export and set [DetectorOutputLayout].
     */
    val detectorOutput: DetectorOutputLayout? = null,
)

/**
 * Layout of the YOLO26 (NMS-free) detector output, written to match your exported `.tflite`. NMS-free
 * means the model emits final, deduplicated boxes directly — the decoder parses them, it does not run
 * NMS. CONFIRM against your export before Phase 2.
 *
 * @param numDetections max boxes emitted.
 * @param attributesPerBox e.g. [x, y, w, h, score, classId] or per-class scores — set to your export.
 * @param boxFormat geometry encoding of the first 4 attributes.
 * @param coordsNormalized true if box coords are already in [0,1].
 * @param classNames detector class id → label, in the export's class order.
 */
data class DetectorOutputLayout(
    val numDetections: Int,
    val attributesPerBox: Int,
    val boxFormat: BoxFormat,
    val coordsNormalized: Boolean,
    val classNames: List<String>,
)

enum class BoxFormat {
    /** centerX, centerY, width, height. */
    XYWH,

    /** left, top, right, bottom. */
    XYXY,
}
