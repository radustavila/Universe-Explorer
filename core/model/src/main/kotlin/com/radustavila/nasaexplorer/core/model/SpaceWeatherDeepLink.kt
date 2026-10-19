package com.radustavila.nasaexplorer.core.model

/**
 * In-app address of the space weather screen, shared by the navigation graph (which resolves it)
 * and the aurora alert notification (which opens it), like [ApodDeepLink].
 */
object SpaceWeatherDeepLink {
    const val URI = "nasaexplorer://spaceweather"
}
