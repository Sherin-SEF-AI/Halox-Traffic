package com.haloxtraffic.feature.detection.di

import com.haloxtraffic.core.sensors.profile.HardwareDelegateProbe
import com.haloxtraffic.feature.detection.runtime.Detector
import com.haloxtraffic.feature.detection.runtime.CompositeDetector
import com.haloxtraffic.feature.detection.runtime.LiteRtDelegateProbe
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the LiteRT-backed implementations. The [HardwareDelegateProbe] binding lives here (not in
 * `:core:sensors`) so the sensors module stays free of ML dependencies but the profiler still gets a
 * real GPU-capability signal.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DetectionModule {

    @Binds
    @Singleton
    abstract fun bindDelegateProbe(impl: LiteRtDelegateProbe): HardwareDelegateProbe

    @Binds
    @Singleton
    abstract fun bindDetector(impl: CompositeDetector): Detector
}
