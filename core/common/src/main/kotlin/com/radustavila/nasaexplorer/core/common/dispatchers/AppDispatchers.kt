package com.radustavila.nasaexplorer.core.common.dispatchers

import javax.inject.Qualifier

/**
 * Dispatchers are injected rather than referenced statically so that every suspending component can
 * be driven by a test dispatcher.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val type: AppDispatcher)

enum class AppDispatcher { IO, Default }

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
