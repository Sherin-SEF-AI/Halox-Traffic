# Build Phases

This repo currently contains **Phase 0 + Phase 1**. Later phases drop into the seams already created.

| Phase | Scope | Status |
|------|-------|--------|
| 0 | Module graph, Hilt/Compose/Nav, design system, Room schema + migrations, domain models, device profiler + tiering, model-provisioning scaffold | ✅ done |
| 1 | CameraX preview + analysis + capture, ring buffer, location/IMU/time Flows, live HUD | ✅ done |
| 2 | YOLO26 LiteRT detector + NMS-free decoder + delegate fallback + live overlay + per-stage latency | ✅ done (`:feature:detection`) — supply a real `.tflite` + confirm output layout to go live |
| 3 | IoU+Kalman tracker + direction estimation; No-Helmet / Triple-Riding / Wrong-Way / No-Plate FSMs + live overlay/haptic | ✅ done (`:feature:violations`) — committed cases are in-memory until Phase 5 sealing |
| 4 | ANPR: plate crop + best-frame + PaddleOCR (CTC) + correction + validation + consensus + colour | ✅ done (`:feature:anpr`) — supply a real PP-OCRv5 `.tflite` + confirm charset/blank to go live |
| 5 | Evidence sealing: seal+persist signed hash-chained cases + immutable store; Case File review | ✅ done (`:core:data` SealingRepository, `:core:evidence` SealedStore, `:feature:casefile`) |
| 6 | Junction geometry capture (camera-overlay) + Red-Light / Seatbelt / Phone / Lane FSMs behind positioning flags | ✅ done (`:feature:violations`, `:feature:map`) — MapLibre map deferred to Phase 8 |
| 7 | Gemma 3n VLM (HIGH tier) for ambiguous verification + hard plates + descriptions | ✅ done (`:feature:vlm`) — supply a Gemma `.task` + confirm tasks-genai version |
| 8 | Reports/export: PDF case-file + e-challan ZIP bundle + CSV + analytics + violation map/heatmap | ✅ done (`:core:export`, `:feature:reports`, `:feature:map`) — map is a dependency-free Canvas; MapLibre tiles optional later |
| 9 | WorkManager sync to the §13 contract; hash-chain self-check; Vahan lookup | ✅ done (`:core:sync`, Sync screen in `:app`) — server-side auth header is a deployment TODO |
| 10 | Hardening: bystander blur, thermal/battery/storage, full test coverage | ⬜ |

## What's deliberately not done yet
- **Detection (Phase 2) is fully implemented but needs a real model asset to light up**: the LiteRT
  interpreter, GPU→NNAPI→XNNPACK fallback, YUV→RGB + letterbox preprocessing, NMS-free decode, live
  overlay and per-stage latency are all real. With the placeholder model URL the detector resolves to
  `NO_MODEL` (download fails) and the analyzer skips frames — never fabricating detections. Supply a
  hosted `.tflite` + SHA-256 in `ModelRegistry` and confirm the output tensor layout to enable inference.
- Tracking + the four high-confidence FSMs (Phase 3) are real and unit-tested. They light up once the
  detector has a real model feeding boxes. `plateConformant` stays null until ANPR (Phase 4) reads plates.
- Evidence sealing (Phase 5) is live: each COMMIT runs ANPR, captures a full-res still + plate crop into
  the immutable sealed store, then seals a signed, hash-chained `ViolationCase` + `EvidencePackage` with
  the original plate read as the first (append-only) audit row. The Case File screen lists/reviews cases,
  shows the integrity (chain + signature) badge, supports Confirm/Dismiss, and append-only plate
  correction. Video-clip encoding (vs. stills) is deferred to Phase 10.
- ANPR (Phase 4) is wired end-to-end: on COMMIT the detector buffers recent frames, crops the
  offending vehicle's plate, ranks by sharpness, runs the PP-OCRv5 CTC recognizer on the top-N, fuses
  via consensus → correction → validation, and classifies plate colour. Needs a real PP-OCRv5 `.tflite`
  (confirm input H×W, channel order, character dictionary + blank index) to produce reads; until then
  the engine stays `NO_MODEL` and returns explicit `unreadable` (never a fabricated plate).
- All 8 violation FSMs now exist (Phase 3 + 6). Viewpoint-dependent ones (red-light/seatbelt/phone/lane)
  are enabled per session only where junction geometry / mount support them. Junction geometry is captured
  over the live camera (image-space stop-line / signal ROI / lane lines) in `:feature:map` JunctionConfig;
  the MapLibre *map* view (§12.5) is folded into Phase 8.
- VLM (Phase 7) is HIGH-tier only and strictly off the hot path: on COMMIT, after sealing, it adds an
  incident description and — only when OCR was uncertain — a plate *candidate* (append-only, never
  validated, never overwriting a real read). Per-session image budget enforced; DISABLED on LOW/MID.
  Needs a Gemma 3n `.task` asset; confirm the `tasks-genai` version + the multimodal API (isolated to
  `MediaPipeVlmEngine`).
- Reports/export (Phase 8): real PDF case-file (PdfDocument), e-challan ZIP bundle (media +
  metadata.json with hash/signature/chain pointer/public key) and CSV index, shared via FileProvider;
  analytics (by type, validated/uncertain rate, repeat plates, by hour). The violation map + heatmap is a
  dependency-free Compose Canvas (offline, lat/lon scatter + density + filters + tap-to-open) — a MapLibre
  tile basemap can replace the scatter later without changing the data/filters/interaction. ML Kit
  (bystander blur) lands in Phase 10.
- Sync (Phase 9): sealing a case / starting a session enqueues an idempotent sync item (keyed by the
  client UUID). SyncWorker drains on connectivity — builds each DTO from the DB, upserts session/case,
  uploads sealed media (multipart), and marks synced / retries with backoff. The Sync & Evidence screen
  shows the pending queue, a "run now" trigger, the hash-chain + signature integrity self-check, storage
  usage and an optional Vahan cross-reference. Attaching the auth token (an OkHttp interceptor) is a
  deployment TODO; the FastAPI server itself is out of scope (client-only, per the agreed plan).
- The TFLite/LiteRT dependency version (`tflite = 2.16.1`) and the YOLO26 output tensor layout are the two
  things to confirm at build time.
