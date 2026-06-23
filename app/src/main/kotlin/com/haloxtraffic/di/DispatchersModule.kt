package com.haloxtraffic.di

import com.haloxtraffic.core.model.DefaultDispatcher
import com.haloxtraffic.core.model.IoDispatcher
import com.haloxtraffic.core.model.MainDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Single source of the coroutine dispatcher bindings used across all modules (qualifiers in :core:model). */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides @IoDispatcher fun io(): CoroutineDispatcher = Dispatchers.IO
    @Provides @DefaultDispatcher fun default(): CoroutineDispatcher = Dispatchers.Default
    @Provides @MainDispatcher fun main(): CoroutineDispatcher = Dispatchers.Main
}
