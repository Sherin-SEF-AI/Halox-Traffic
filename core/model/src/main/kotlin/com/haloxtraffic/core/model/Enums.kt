package com.haloxtraffic.core.model

import kotlinx.serialization.Serializable

/**
 * Device capability tier. Drives model variant selection, inference cadence, input resolution and
 * whether the on-device VLM (Gemma 3n) is available. See [DetectionConfig].
 */
@Serializable
enum class DeviceTier { LOW, MID, HIGH }

/** How the phone is positioned, which sets framing/cadence defaults and an expected-direction hint. */
@Serializable
enum class MountMode { HANDHELD, DASHBOARD, FIXED_TRIPOD }

/** Review lifecycle of a [ViolationType] case. Cases are append-only; status changes are audited. */
@Serializable
enum class CaseStatus { OPEN, REVIEWED, CONFIRMED, DISMISSED }

/**
 * Trust level of the timestamp attached to a violation. Evidence never drops on weak time — it is
 * flagged. GPS/NTP-anchored time is trusted; a bare device wall clock is not.
 */
@Serializable
enum class TimeTrust {
    /** Anchored to GPS or NTP; defensible. */
    TRUSTED,

    /** Device wall clock only, no external anchor. Surfaced as amber in the HUD. */
    UNTRUSTED,
}

/** Coarse vehicle class emitted by the detector and used to scope violation FSMs. */
@Serializable
enum class VehicleClass { MOTORCYCLE, CAR, AUTO_RICKSHAW, TRUCK, BUS, UNKNOWN }

/**
 * Indian number-plate background colour → vehicle category. Stored as evidence metadata; mismatches
 * (e.g. a private white plate in a commercial-only context) can be flagged downstream.
 */
@Serializable
enum class PlateColor(val category: String) {
    WHITE("private"),
    YELLOW("commercial"),
    GREEN("electric"),
    BLACK("self-drive rental"),
    RED("temporary"),
    BLUE("diplomatic"),
    UNKNOWN("unknown"),
}

/** Recognised Indian plate format families, resolved by the ANPR validator. */
@Serializable
enum class PlateFormat {
    /** XX NN XX NNNN — standard state-series HSRP. */
    STANDARD,

    /** YY BH NNNN XX — Bharat (BH) series. */
    BH_SERIES,

    /** Leading T temporary registration. */
    TEMPORARY,

    /** Detected as a plate but not conformant to any known format. */
    NON_CONFORMANT,
}

/** Active inference delegate, logged for observability. Selected via the fallback chain. */
@Serializable
enum class InferenceDelegate { GPU, NNAPI, XNNPACK_CPU }

/** Numeric model precision. INT8 for LOW/MID, FP16 acceptable on HIGH. */
@Serializable
enum class Quantization { INT8, FP16 }
