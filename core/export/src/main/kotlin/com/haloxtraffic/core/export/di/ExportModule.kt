package com.haloxtraffic.core.export.di

import com.haloxtraffic.core.export.DefaultEvidenceExporter
import com.haloxtraffic.core.export.EvidenceExporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExportModule {
    @Binds
    @Singleton
    abstract fun bindEvidenceExporter(impl: DefaultEvidenceExporter): EvidenceExporter
}
