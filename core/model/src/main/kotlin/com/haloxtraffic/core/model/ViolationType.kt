package com.haloxtraffic.core.model

import kotlinx.serialization.Serializable

/**
 * Indian traffic violation catalog (§6). Each type is backed by a deterministic FSM in
 * `:feature:violations`. [viewpointDependent] types only fire when the camera geometry supports them
 * (e.g. a stop-line is in frame), gated behind a positioning flag.
 *
 * @param displayName human-readable label for the HUD/case file.
 * @param appliesTo vehicle classes this violation can be raised against.
 * @param viewpointDependent true if reliable detection needs a specific viewpoint/geometry.
 */
@Serializable
enum class ViolationType(
    val displayName: String,
    val appliesTo: Set<VehicleClass>,
    val viewpointDependent: Boolean,
) {
    NO_HELMET(
        "No Helmet",
        setOf(VehicleClass.MOTORCYCLE),
        viewpointDependent = false,
    ),
    TRIPLE_RIDING(
        "Triple Riding / Overloading",
        setOf(VehicleClass.MOTORCYCLE),
        viewpointDependent = false,
    ),
    WRONG_WAY(
        "Wrong-Way Driving",
        setOf(VehicleClass.MOTORCYCLE, VehicleClass.CAR, VehicleClass.AUTO_RICKSHAW, VehicleClass.TRUCK, VehicleClass.BUS),
        viewpointDependent = false,
    ),
    PLATE_MISSING_OR_OBSCURED(
        "No / Obscured / Non-conformant Plate",
        setOf(VehicleClass.MOTORCYCLE, VehicleClass.CAR, VehicleClass.AUTO_RICKSHAW, VehicleClass.TRUCK, VehicleClass.BUS),
        viewpointDependent = false,
    ),
    RED_LIGHT_JUMP(
        "Red-Light Jumping",
        setOf(VehicleClass.MOTORCYCLE, VehicleClass.CAR, VehicleClass.AUTO_RICKSHAW, VehicleClass.TRUCK, VehicleClass.BUS),
        viewpointDependent = true,
    ),
    NO_SEATBELT(
        "No Seatbelt",
        setOf(VehicleClass.CAR, VehicleClass.TRUCK),
        viewpointDependent = true,
    ),
    PHONE_USE(
        "Mobile Phone Use While Driving",
        setOf(VehicleClass.MOTORCYCLE, VehicleClass.CAR, VehicleClass.AUTO_RICKSHAW, VehicleClass.TRUCK, VehicleClass.BUS),
        viewpointDependent = true,
    ),
    LANE_VIOLATION(
        "Lane / Line Violation",
        setOf(VehicleClass.CAR, VehicleClass.AUTO_RICKSHAW, VehicleClass.TRUCK, VehicleClass.BUS),
        viewpointDependent = true,
    );

    companion object {
        /** Types robust from arbitrary mobile viewpoints — enabled by default on every mount mode. */
        val highConfidence: Set<ViolationType>
            get() = entries.filterNot { it.viewpointDependent }.toSet()
    }
}
