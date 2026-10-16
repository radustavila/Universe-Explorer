package com.radustavila.nasaexplorer.core.model

import java.time.LocalDate

/**
 * In-app address of one entry's details screen. Shared by the navigation graph, which resolves it,
 * and by the surfaces outside the app (notification, home screen widget), which open it - so neither
 * side has to know the other's internals.
 */
object ApodDeepLink {
    const val BASE_URI = "nasaexplorer://apod"

    fun uriFor(date: LocalDate): String = "$BASE_URI/$date"
}
