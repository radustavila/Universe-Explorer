package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.SearchHit
import com.radustavila.nasaexplorer.core.model.SearchSource
import com.radustavila.nasaexplorer.core.model.SearchTarget
import java.time.LocalDate

object TestSearchHits {

    fun apod(title: String, date: LocalDate = LocalDate.of(2024, 1, 1), snippet: String? = null): SearchHit =
        SearchHit(
            source = SearchSource.APOD,
            id = date.toString(),
            title = title,
            target = SearchTarget.ApodEntry(date),
            snippet = snippet,
            date = date,
        )

    fun exoplanet(name: String, host: String = name.substringBeforeLast(' ')): SearchHit = SearchHit(
        source = SearchSource.EXOPLANETS,
        id = name,
        title = name,
        target = SearchTarget.ExoplanetEntry(name),
        subtitle = host,
    )

    fun nasaMedia(nasaId: String, title: String): SearchHit = SearchHit(
        source = SearchSource.NASA_LIBRARY,
        id = nasaId,
        title = title,
        target = SearchTarget.NasaMediaItem(nasaId),
    )

    fun launch(id: String, name: String): SearchHit = SearchHit(
        source = SearchSource.LAUNCHES,
        id = id,
        title = name,
        target = SearchTarget.LaunchEntry(id),
    )
}
