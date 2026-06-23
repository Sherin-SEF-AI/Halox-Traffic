package com.haloxtraffic.core.model

/**
 * Canonical detector output classes (§4/§6). The ordinal is the class id the model emits, so the
 * exported YOLO26 model's class order MUST match this enum's order. Centralised here so both the
 * detector (label mapping) and the violation FSMs (semantic meaning) agree on one taxonomy.
 *
 * @param label string label, also used by `ModelRegistry.detectorClasses`.
 * @param vehicleClass the [VehicleClass] this maps to, or null for non-vehicle classes.
 */
enum class DetectionClass(val label: String, val vehicleClass: VehicleClass?) {
    MOTORCYCLE("motorcycle", VehicleClass.MOTORCYCLE),
    CAR("car", VehicleClass.CAR),
    AUTO_RICKSHAW("auto_rickshaw", VehicleClass.AUTO_RICKSHAW),
    TRUCK("truck", VehicleClass.TRUCK),
    BUS("bus", VehicleClass.BUS),
    PERSON("person", null),
    HELMET("helmet", null),
    NO_HELMET("no_helmet", null),
    PLATE("plate", null),
    TRAFFIC_LIGHT_RED("traffic_light_red", null),
    TRAFFIC_LIGHT_GREEN("traffic_light_green", null),
    PHONE("phone", null),
    SEATBELT("seatbelt", null);

    val isVehicle: Boolean get() = vehicleClass != null

    companion object {
        /** Detector class id → class, or null if out of range. */
        fun fromId(id: Int): DetectionClass? = entries.getOrNull(id)

        /** Labels in class-id order, for the model registry + overlay. */
        val labels: List<String> = entries.map { it.label }

        val vehicleClasses: List<DetectionClass> = entries.filter { it.isVehicle }
    }
}
