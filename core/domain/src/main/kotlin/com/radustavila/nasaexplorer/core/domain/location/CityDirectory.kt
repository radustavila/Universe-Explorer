package com.radustavila.nasaexplorer.core.domain.location

import com.radustavila.nasaexplorer.core.model.City

/** Finds places by name, for users who would rather not share their location. Works offline. */
interface CityDirectory {

    /** Cities whose name starts with, then contains, [query]; empty for a blank query. */
    suspend fun search(query: String, limit: Int = DEFAULT_LIMIT): List<City>

    companion object {
        const val DEFAULT_LIMIT = 8
    }
}
