package com.haloxtraffic.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.haloxtraffic.core.data.dao.EvidencePackageDao
import com.haloxtraffic.core.data.dao.JunctionDao
import com.haloxtraffic.core.data.dao.JurisdictionDao
import com.haloxtraffic.core.data.dao.PlateAuditDao
import com.haloxtraffic.core.data.dao.SessionDao
import com.haloxtraffic.core.data.dao.SyncQueueDao
import com.haloxtraffic.core.data.dao.VehicleDao
import com.haloxtraffic.core.data.dao.ViolationCaseDao
import com.haloxtraffic.core.data.entity.EvidencePackageEntity
import com.haloxtraffic.core.data.entity.JunctionEntity
import com.haloxtraffic.core.data.entity.JurisdictionEntity
import com.haloxtraffic.core.data.entity.PlateAuditEntity
import com.haloxtraffic.core.data.entity.SessionEntity
import com.haloxtraffic.core.data.entity.SyncQueueItemEntity
import com.haloxtraffic.core.data.entity.VehicleEntity
import com.haloxtraffic.core.data.entity.ViolationCaseEntity

@Database(
    entities = [
        JurisdictionEntity::class,
        JunctionEntity::class,
        SessionEntity::class,
        VehicleEntity::class,
        ViolationCaseEntity::class,
        EvidencePackageEntity::class,
        PlateAuditEntity::class,
        SyncQueueItemEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class HaloxDatabase : RoomDatabase() {
    abstract fun jurisdictionDao(): JurisdictionDao
    abstract fun junctionDao(): JunctionDao
    abstract fun sessionDao(): SessionDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun violationCaseDao(): ViolationCaseDao
    abstract fun evidencePackageDao(): EvidencePackageDao
    abstract fun plateAuditDao(): PlateAuditDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        const val NAME = "haloxtraffic.db"

        /**
         * Migrations registered here as the schema evolves. v1 is the baseline; future versions add
         * `Migration(n, n+1)` objects. Destructive migration is intentionally NOT enabled — evidence
         * must never be silently dropped.
         */
        val MIGRATIONS = emptyArray<androidx.room.migration.Migration>()
    }
}
