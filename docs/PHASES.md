# Build Phases

This repo currently contains **Phase 0 + Phase 1**. Later phases drop into the seams already created.

| Phase | Scope | Status |
|------|-------|--------|
| 0 | Module graph, Hilt/Compose/Nav, design system, Room schema + migrations, domain models, device profiler + tiering, model-provisioning scaffold | ✅ done |
| 1 | CameraX preview + analysis + capture, ring buffer, location/IMU/time Flows, live HUD | ✅ done |
| 2 | YOLO26 LiteRT detector + NMS-free decoder + delegate fallback + live overlay + per-stage latency | ✅ done (`:feature:detection`) — supply a real `.tflite` + confirm output layout to go live |
| 3 | IoU+Kalman tracker + direction estimation; No-Helmet / Triple-Riding / Wrong-Way / No-Plate FSMs + live overlay/haptic | ✅ done (`:feature:violations`) — committed cases are in-memory until Phase 5 sealing |
| 4 | ANPR: plate detect + best-frame + PaddleOCR + correction + validation + consensus | ⬜ validator/corrector/consensus done; recognizer pending (`:feature:anpr`) |
| 5 | Evidence sealing: package builder + hash-chain + Keystore signing + sealed store; Case File | ⬜ crypto done (`:core:evidence`); wiring pending |
| 6 | Junction config (stop-line / signal ROI / lanes) on MapLibre; remaining FSMs | ⬜ |
| 7 | Gemma 3n VLM (HIGH tier) for ambiguous verification + hard plates + descriptions | ⬜ |
| 8 | Reports/export: case-file PDF + e-challan bundle + CSV + analytics + map | ⬜ exporter contract ready (`:core:export`) |
| 9 | WorkManager sync to the §13 contract; hash-chain self-check; Vahan lookup | ⬜ client contract ready (`:core:sync`) |
| 10 | Hardening: bystander blur, thermal/battery/storage, full test coverage | ⬜ |

## What's deliberately not done yet
- **Detection (Phase 2) is fully implemented but needs a real model asset to light up**: the LiteRT
  interpreter, GPU→NNAPI→XNNPACK fallback, YUV→RGB + letterbox preprocessing, NMS-free decode, live
  overlay and per-stage latency are all real. With the placeholder model URL the detector resolves to
  `NO_MODEL` (download fails) and the analyzer skips frames — never fabricating detections. Supply a
  hosted `.tflite` + SHA-256 in `ModelRegistry` and confirm the output tensor layout to enable inference.
- Tracking + the four high-confidence FSMs (Phase 3) are real and unit-tested; COMMITs are in-memory
  (haptic + red overlay + session count) and get sealed to evidence in Phase 5. They light up once the
  detector has a real model feeding boxes. `plateConformant` stays null until ANPR (Phase 4) reads plates.
- OCR recognizer (Phase 4) still pending — the validator/corrector/consensus are done.
- MediaPipe (VLM), MapLibre and ML Kit deps remain commented in their `build.gradle.kts` until their phases.
- The TFLite/LiteRT dependency version (`tflite = 2.16.1`) and the YOLO26 output tensor layout are the two
  things to confirm at build time.
