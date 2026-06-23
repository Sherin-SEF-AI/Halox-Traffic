# HaloxTraffic

On-device Indian traffic-violation detection, ANPR, and tamper-evident evidence collection for Android.
Offline-first, device-tiered, evidence-grade. Codename **VAHAN-EYE**.

> **Build status:** All 11 phases (0–10) are implemented end-to-end: device tiering, CameraX capture,
> YOLO26 detection, IoU+Kalman tracking, all 8 violation FSMs, ANPR (PaddleOCR), tamper-evident evidence
> sealing + Case File, junction geometry, Gemma 3n VLM, reports/export (PDF / e-challan / CSV), violation
> map, offline-first sync + integrity self-check, and hardening (bystander blur, retention, battery). The
> three on-device ML models (detector / OCR / VLM) are provisioned at runtime — **supply the trained
> assets** (`docs/MODELS.md`) to enable live inference. See `docs/PHASES.md` for per-phase detail and the
> flagged dependency/asset confirmations.

## Requirements
- **JDK 17**
- **Android SDK 35** (compileSdk/targetSdk 35, minSdk 26)
- Android Studio Ladybug+ (or run Gradle directly)

## First build
The binary `gradle/wrapper/gradle-wrapper.jar` is **not** committed (it is a binary). Generate it once:

```bash
# Option A: open the project in Android Studio — it regenerates the wrapper on sync.
# Option B: with a local Gradle 8.11+ installed:
gradle wrapper --gradle-version 8.11.1
```

Then:

```bash
./gradlew assembleDebug
./gradlew :app:installDebug      # to an adb-connected device
./gradlew test                   # unit suite
```

## Module graph
```
:app
:core:designsystem   Operational Materialism design system
:core:model          domain enums + value types
:core:data           Room + DataStore + repositories
:core:sensors        CameraX, location, IMU, time, device profiler
:core:evidence       SHA-256 + hash-chain + Keystore signing
:core:export         evidence export (skeleton)
:core:sync           Retrofit client + WorkManager (client-only)
:feature:detection   YOLO26 LiteRT detector + tracking buffer + model provisioning
:feature:violations  IoU+Kalman tracker + 8 violation FSMs + junction geometry
:feature:anpr        PaddleOCR plate pipeline (best-frame, CTC, consensus, colour)
:feature:vlm         Gemma 3n VLM (HIGH tier, off hot path)
:feature:capture     live enforcement HUD (core screen)
:feature:casefile    case review + export + audited plate correction
:feature:map         junction geometry config + violation map/heatmap
:feature:reports     reports + analytics + CSV export
:feature:settings    settings (tier, retention, privacy)
```

## ML models
Models are **not** committed. They are downloaded + integrity-verified at first launch by the
provisioning step (`:feature:detection` `ModelProvisioner`). Supply your exported assets and their SHA-256
hashes in `ModelRegistry`. See `docs/MODELS.md`.

## License / use
Evidence-collection tool for authorized enforcement / audit use. It produces enforcement-grade evidence;
it does not itself issue challans.
