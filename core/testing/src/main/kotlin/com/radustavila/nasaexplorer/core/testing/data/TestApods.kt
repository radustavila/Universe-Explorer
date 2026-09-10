package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.Apod
import com.radustavila.nasaexplorer.core.model.MediaType
import java.time.Instant
import java.time.LocalDate

object TestApods {

    val CACHED_AT: Instant = Instant.parse("2026-09-10T12:00:00Z")

    fun image(
        date: LocalDate = LocalDate.of(2026, 9, 10),
        title: String = "Spiral Galaxy NGC 1300",
        isFavorite: Boolean = false,
    ): Apod = Apod(
        date = date,
        title = title,
        explanation = "A barred spiral galaxy in the constellation Eridanus.",
        mediaType = MediaType.IMAGE,
        url = "https://apod.nasa.gov/apod/image/ngc1300.jpg",
        hdUrl = "https://apod.nasa.gov/apod/image/ngc1300_hd.jpg",
        thumbnailUrl = null,
        copyright = "NASA",
        isFavorite = isFavorite,
        cachedAt = CACHED_AT,
    )

    fun video(
        date: LocalDate = LocalDate.of(2026, 9, 9),
        isFavorite: Boolean = false,
    ): Apod = Apod(
        date = date,
        title = "Flight Through the Orion Nebula",
        explanation = "A visualization of the Orion Nebula.",
        mediaType = MediaType.VIDEO,
        url = "https://www.youtube.com/embed/abc123",
        hdUrl = null,
        thumbnailUrl = "https://img.youtube.com/vi/abc123/0.jpg",
        copyright = null,
        isFavorite = isFavorite,
        cachedAt = CACHED_AT,
    )

    fun feed(size: Int, startDate: LocalDate = LocalDate.of(2026, 9, 10)): List<Apod> =
        (0 until size).map { offset ->
            image(date = startDate.minusDays(offset.toLong()), title = "Apod #$offset")
        }
}
