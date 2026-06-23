# Models

Models are **not committed**. They are downloaded + SHA-256-verified + cached at first use by
`ModelProvisioner` ([feature/detection](../feature/detection/src/main/kotlin/com/haloxtraffic/feature/detection/model/ModelProvisioner.kt)).

## Required assets (per tier)

| Kind | Variant by tier | Source | Notes |
|------|-----------------|--------|-------|
| Detector | `yolo26n` (LOW) / `yolo26s` (MID) / `yolo26m` (HIGH) | Ultralytics YOLO26, exported to LiteRT | NMS-free; confirm output tensor layout |
| Plate OCR | PP-OCRv5 `tiny` / `small` / `small`–`medium` | PaddleOCR, **fine-tuned on Indian plates** | Paddle-Lite or ONNX/LiteRT |
| VLM | Gemma 3n E2B/E4B | MediaPipe `.task`/`.litertlm` | HIGH tier only |

## Wiring real assets
1. Export the detector: `yolo export model=yolo26n.pt format=tflite int8=True`.
2. **Confirm the output tensor layout** and set it in
   [`ModelRegistry.detectorSpec`](../feature/detection/src/main/kotlin/com/haloxtraffic/feature/detection/model/ModelRegistry.kt)
   (`DetectorOutputLayout`: attribute order, box format, normalization, class order).
3. Host the files and set `BASE_URL` + each `sha256` in `ModelRegistry`. The provisioner fails closed on
   a hash mismatch — a tampered or partial model is never loaded.
4. Uncomment the LiteRT deps in `feature/detection/build.gradle.kts` and implement the interpreter calls
   in `LiteRtDetector.detect` (the decoder `Yolo26Decoder` is already written and unit-tested).

## Class order (must match the export)
See `ModelRegistry.detectorClasses`. The violation FSMs depend on these classes
(motorcycle, car, auto_rickshaw, truck, bus, person, helmet, no_helmet, plate, traffic_light_red/green,
phone, seatbelt).
