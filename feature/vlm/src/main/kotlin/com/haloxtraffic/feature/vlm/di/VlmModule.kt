package com.haloxtraffic.feature.vlm.di

import com.haloxtraffic.feature.vlm.MediaPipeVlmEngine
import com.haloxtraffic.feature.vlm.VlmEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VlmModule {
    @Binds
    @Singleton
    abstract fun bindVlmEngine(impl: MediaPipeVlmEngine): VlmEngine
}
