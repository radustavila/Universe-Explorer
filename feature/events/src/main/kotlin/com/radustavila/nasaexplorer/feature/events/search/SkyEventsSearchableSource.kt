package com.radustavila.nasaexplorer.feature.events.search

import android.content.Context
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.designsystem.skyevent.SkyEventText
import com.radustavila.nasaexplorer.core.domain.search.SearchText
import com.radustavila.nasaexplorer.core.domain.search.SearchableSource
import com.radustavila.nasaexplorer.core.domain.search.readLocally
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSkyEventsUseCase
import com.radustavila.nasaexplorer.core.model.CometPerihelion
import com.radustavila.nasaexplorer.core.model.MeteorShowerPeak
import com.radustavila.nasaexplorer.core.model.PlanetOpposition
import com.radustavila.nasaexplorer.core.model.SearchHit
import com.radustavila.nasaexplorer.core.model.SearchSource
import com.radustavila.nasaexplorer.core.model.SearchTarget
import com.radustavila.nasaexplorer.core.model.SkyEvent
import com.radustavila.nasaexplorer.core.model.SkyEventCategory
import com.radustavila.nasaexplorer.feature.events.R
import com.radustavila.nasaexplorer.feature.events.component.listedTime
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * The coming year's sky events by name ("Perseids", "eclipse", "full Moon", "Mars"), soonest
 * first. Computed on the device, so it lives here, where the localised names are: the names are the
 * app's own words, not data from a source, and matching has to happen in the user's language.
 */
class SkyEventsSearchableSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val observeSkyEvents: ObserveSkyEventsUseCase,
    private val timeProvider: TimeProvider,
) : SearchableSource {

    override val source = SearchSource.SKY_EVENTS

    override suspend fun search(query: String, limit: Int): DataResult<List<SearchHit>> = readLocally {
        val terms = SearchText.terms(query)
        val text = SkyEventText(context.resources)
        val now = timeProvider.now()
        val zone = ZoneId.systemDefault()
        observeSkyEvents(from = now, until = now.plus(HORIZON)).first()
            .asSequence()
            .map { event -> event to text.title(event) }
            .filter { (event, title) ->
                SearchText.matchesAll(terms, title, event.category.searchWords(), event.detail())
            }
            .take(limit)
            .map { (event, title) ->
                SearchHit(
                    source = source,
                    id = event.id,
                    title = title,
                    target = SearchTarget.SkyEventEntry(event.id),
                    subtitle = event.detail(),
                    date = event.listedTime.atZone(zone).toLocalDate(),
                )
            }
            .toList()
    }

    private fun SkyEventCategory.searchWords(): String = context.getString(
        when (this) {
            SkyEventCategory.ECLIPSES -> R.string.sky_events_search_words_eclipses
            SkyEventCategory.MOON -> R.string.sky_events_search_words_moon
            SkyEventCategory.PLANETS -> R.string.sky_events_search_words_planets
            SkyEventCategory.METEORS -> R.string.sky_events_search_words_meteors
            SkyEventCategory.COMETS -> R.string.sky_events_search_words_comets
            SkyEventCategory.SEASONS -> R.string.sky_events_search_words_seasons
        },
    )

    /** A name from the data worth showing and matching: the constellation, the shower's parent body or the comet. */
    private fun SkyEvent.detail(): String? = when (this) {
        is PlanetOpposition -> constellation
        is MeteorShowerPeak -> parentBody
        is CometPerihelion -> name
        else -> null
    }

    private companion object {
        val HORIZON: Duration = Duration.ofDays(366)
    }
}
