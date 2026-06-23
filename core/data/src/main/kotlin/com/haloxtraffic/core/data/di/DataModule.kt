package com.haloxtraffic.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.haloxtraffic.core.data.HaloxDatabase
import com.haloxtraffic.core.data.dao.EvidencePackageDao
import com.haloxtraffic.core.data.dao.JunctionDao
import com.haloxtraffic.core.data.dao.JurisdictionDao
import com.haloxtraffic.core.data.dao.PlateAuditDao
import com.haloxtraffic.core.data.dao.SessionDao
import com.haloxtraffic.core.data.dao.SyncQueueDao
import com.haloxtraffic.core.data.dao.VehicleDao
import com.haloxtraffic.core.data.dao.ViolationCaseDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HaloxDatabase =
        Room.databaseBuilder(context, HaloxDatabase::class.java, HaloxDatabase.NAME)
            .addMigrations(*HaloxDatabase.MIGRATIONS)
            .build()

    @Provides fun jurisdictionDao(db: HaloxDatabase): JurisdictionDao = db.jurisdictionDao()
    @Provides fun junctionDao(db: HaloxDatabase): JunctionDao = db.junctionDao()
    @Provides fun sessionDao(db: HaloxDatabase): SessionDao = db.sessionDao()
    @Provides fun vehicleDao(db: HaloxDatabase): VehicleDao = db.vehicleDao()
    @Provides fun violationCaseDao(db: HaloxDatabase): ViolationCaseDao = db.violationCaseDao()
    @Provides fun evidencePackageDao(db: HaloxDatabase): EvidencePackageDao = db.evidencePackageDao()
    @Provides fun plateAuditDao(db: HaloxDatabase): PlateAuditDao = db.plateAuditDao()
    @Provides fun syncQueueDao(db: HaloxDatabase): SyncQueueDao = db.syncQueueDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("halox_settings")
        }
}
