# Build Phases

This repo currently contains **Phase 0 + Phase 1**. Later phases drop into the seams already created.

| Phase | Scope | Status |
|------|-------|--------|
| 0 | Module graph, Hilt/Compose/Nav, design system, Room schema + migrations, domain models, device profiler + tiering, model-provisioning scaffold | ✅ done |
| 1 | CameraX preview + analysis + capture, ring buffer, location/IMU/time Flows, live HUD | ✅ done |
| 2 | YOLO26 LiteRT detector + NMS-free decoder + delegate fallback (real model) | ⬜ seams ready (`:feature:detection`) |
| 3 | IoU+Kalman tracker + direction estimation; No-Helmet / Triple-Riding / Wrong-Way / No-Plate FSMs | ⬜ FSM contract ready (`:feature:violations`) |
| 4 | ANPR: plate detect + best-frame + PaddleOCR + correction + validation + consensus | ⬜ validator/corrector/consensus done; recognizer pending (`:feature:anpr`) |
| 5 | Evidence sealing: package builder + hash-chain + Keystore signing + sealed store; Case File | ⬜ crypto done (`:core:evidence`); wiring pending |
| 6 | Junction config (stop-line / signal ROI / lanes) on MapLibre; remaining FSMs | ⬜ |
| 7 | Gemma 3n VLM (HIGH tier) for ambiguous verification + hard plates + descriptions | ⬜ |
| 8 | Reports/export: case-file PDF + e-challan bundle + CSV + analytics + map | ⬜ exporter contract ready (`:core:export`) |
| 9 | WorkManager sync to the §13 contract; hash-chain self-check; Vahan lookup | ⬜ client contract ready (`:core:sync`) |
| 10 | Hardening: bystander blur, thermal/battery/storage, full test coverage | ⬜ |

## What's deliberately not done yet
- No committed real-time inference against real models (Phase 2/4) — detector/OCR engines throw until
  initialised and never emit fabricated detections.
- Native ML deps (LiteRT, MediaPipe, MapLibre, ML Kit) are commented out in the relevant `build.gradle.kts`
  with TODOs so the Phase-1 foundation builds without unverified coordinates. Bring them online per phase
  after confirming versions.
