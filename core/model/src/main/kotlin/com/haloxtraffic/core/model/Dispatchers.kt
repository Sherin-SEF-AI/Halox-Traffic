package com.haloxtraffic.core.model

import javax.inject.Qualifier

/**
 * Coroutine dispatcher qualifiers, shared so any module can request a specific dispatcher. The actual
 * `CoroutineDispatcher` bindings are provided once in `:app` (`DispatchersModule`). Heavy work
 * (inference, evidence sealing, IO) must never run on the main dispatcher.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher
