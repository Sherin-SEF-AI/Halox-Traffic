package com.haloxtraffic.feature.anpr.di

import com.haloxtraffic.feature.anpr.OnnxOcrEngine
import com.haloxtraffic.feature.anpr.PlateOcrEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AnprModule {
    @Binds
    @Singleton
    abstract fun bindPlateOcrEngine(impl: OnnxOcrEngine): PlateOcrEngine
}
