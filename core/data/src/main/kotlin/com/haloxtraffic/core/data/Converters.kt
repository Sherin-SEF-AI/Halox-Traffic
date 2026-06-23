package com.haloxtraffic.core.data

import androidx.room.TypeConverter
import com.haloxtraffic.core.model.CaseStatus
import com.haloxtraffic.core.model.DeviceTier
import com.haloxtraffic.core.model.MountMode
import com.haloxtraffic.core.model.PlateColor
import com.haloxtraffic.core.model.TimeTrust
import com.haloxtraffic.core.model.VehicleClass
import com.haloxtraffic.core.model.ViolationType

/**
 * Room type converters. Enums are stored by name (stable across versions as long as constants aren't
 * renamed); list/complex fields are stored as JSON strings on the entities directly.
 */
class Converters {
    @TypeConverter fun mountMode(v: MountMode): String = v.name
    @TypeConverter fun toMountMode(v: String): MountMode = MountMode.valueOf(v)

    @TypeConverter fun deviceTier(v: DeviceTier): String = v.name
    @TypeConverter fun toDeviceTier(v: String): DeviceTier = DeviceTier.valueOf(v)

    @TypeConverter fun vehicleClass(v: VehicleClass): String = v.name
    @TypeConverter fun toVehicleClass(v: String): VehicleClass = VehicleClass.valueOf(v)

    @TypeConverter fun plateColor(v: PlateColor?): String? = v?.name
    @TypeConverter fun toPlateColor(v: String?): PlateColor? = v?.let { PlateColor.valueOf(it) }

    @TypeConverter fun violationType(v: ViolationType): String = v.name
    @TypeConverter fun toViolationType(v: String): ViolationType = ViolationType.valueOf(v)

    @TypeConverter fun caseStatus(v: CaseStatus): String = v.name
    @TypeConverter fun toCaseStatus(v: String): CaseStatus = CaseStatus.valueOf(v)

    @TypeConverter fun timeTrust(v: TimeTrust): String = v.name
    @TypeConverter fun toTimeTrust(v: String): TimeTrust = TimeTrust.valueOf(v)
}
