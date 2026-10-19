package com.radustavila.nasaexplorer.core.domain.location

import com.radustavila.nasaexplorer.core.model.ObserverLocation
import kotlinx.coroutines.flow.Flow

/**
 * The one place the app remembers where the user is, whichever feature asked: Sky tonight sets it,
 * and anything else that depends on the observer's position (aurora alerts) reads it from here.
 */
interface ObserverLocationRepository {

    /** The saved location, or null until the user has shared or entered one. */
    val location: Flow<ObserverLocation?>

    suspend fun setLocation(location: ObserverLocation)

    suspend fun clearLocation()
}
