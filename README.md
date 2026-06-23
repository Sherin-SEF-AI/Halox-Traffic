# HaloxTraffic

On-device Indian traffic-violation detection, ANPR, and tamper-evident evidence collection for Android.
Offline-first, device-tiered, evidence-grade. Codename **VAHAN-EYE**.

> **Build status:** This repository contains the **Phase 0 + Phase 1 foundation** — the full multi-module
> skeleton, design system, data layer, device tiering, sensors + capture, and the live enforcement HUD.
> Real-time ML inference (Phase 2+), violation FSMs, ANPR, evidence sealing, VLM, reports and sync wiring
> are scoped for later phases; their module seams and contracts already exist. See
> `docs/PHASES.md` for the roadmap.

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
:feature:detection   YOLO26 LiteRT detector + model provisioning (skeleton)
:feature:violations  violation FSM engine (skeleton)
:feature:anpr        plate OCR pipeline (skeleton)
:feature:capture     live enforcement HUD (Phase-1 core screen)
:feature:casefile    case review (skeleton)
:feature:map         MapLibre violation map (skeleton)
:feature:reports     reports + analytics (skeleton)
:feature:settings    settings (skeleton)
```

## ML models
Models are **not** committed. They are downloaded + integrity-verified at first launch by the
provisioning step (`:feature:detection` `ModelProvisioner`). Supply your exported assets and their SHA-256
hashes in `ModelRegistry`. See `docs/MODELS.md`.

## License / use
Evidence-collection tool for authorized enforcement / audit use. It produces enforcement-grade evidence;
it does not itself issue challans.
