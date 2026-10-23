package com.radustavila.nasaexplorer.data.earthevents.search

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.earthquake.EarthquakeRepository
import com.radustavila.nasaexplorer.core.domain.search.SearchText
import com.radustavila.nasaexplorer.core.domain.search.SearchableSource
import com.radustavila.nasaexplorer.core.domain.search.readLocally
import com.radustavila.nasaexplorer.core.model.EarthquakeFilter
import com.radustavila.nasaexplorer.core.model.SearchHit
import com.radustavila.nasaexplorer.core.model.SearchSource
import com.radustavila.nasaexplorer.core.model.SearchTarget
import java.time.ZoneOffset
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Cached quakes by place ("Italy", "Alaska"), strongest first, as far back as the map shows them.
 * The magnitude is data ("M 5.7"), the place USGS's own name for it.
 */
class EarthquakeSearchableSource @Inject constructor(
    private val repository: EarthquakeRepository,
    private val timeProvider: TimeProvider,
) : SearchableSource {

    override val source = SearchSource.EARTHQUAKES

    override suspend fun search(query: String, limit: Int): DataResult<List<SearchHit>> = readLocally {
        val terms = SearchText.terms(query)
        val now = timeProvider.now()
        repository.observeEarthquakes().first()
            .asSequence()
            .filter { quake -> EarthquakeFilter.entries.any { it.matches(quake, now) } }
            .filter { quake -> quake.place?.let { SearchText.matchesAll(terms, it) } == true }
            .sortedByDescending { it.magnitude }
            .take(limit)
            .map { quake ->
                SearchHit(
                    source = source,
                    id = quake.id,
                    // USGS's "61 km WNW of" is in kilometres; the place name alone reads in any units.
                    title = quake.parsedPlace?.name ?: quake.place.orEmpty(),
                    target = SearchTarget.EarthquakeEntry(quake.id),
                    subtitle = "M ${MAGNITUDE_FORMAT.format(Locale.ROOT, quake.magnitude)}",
                    date = quake.time.atOffset(ZoneOffset.UTC).toLocalDate(),
                )
            }
            .toList()
    }

    private companion object {
        const val MAGNITUDE_FORMAT = "%.1f"
    }
}
