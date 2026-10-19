package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.NasaMedia
import com.radustavila.nasaexplorer.core.model.NasaMediaType
import java.time.LocalDate

object TestNasaMedia {

    fun image(
        nasaId: String = "PIA26702",
        title: String = "Webb Data Reveals Dark Matter",
    ): NasaMedia = NasaMedia(
        nasaId = nasaId,
        title = title,
        description = "Nearly 800,000 galaxies overlaid with a map of dark matter.",
        mediaType = NasaMediaType.IMAGE,
        dateCreated = LocalDate.of(2026, 1, 26),
        center = "JPL",
        creator = "NASA/STScI",
        keywords = listOf("Webb", "Dark Matter"),
        thumbnailUrl = "https://images-assets.nasa.gov/image/$nasaId/$nasaId~thumb.jpg",
        imageUrl = "https://images-assets.nasa.gov/image/$nasaId/$nasaId~large.jpg",
        fullImageUrl = "https://images-assets.nasa.gov/image/$nasaId/$nasaId~orig.png",
        originalUrl = "https://images-assets.nasa.gov/image/$nasaId/$nasaId~orig.png",
        altText = "Galaxies overlaid with a blue map of dark matter.",
    )

    fun video(
        nasaId: String = "Webb Journey to Space Ep2",
        title: String = "Webb Journey to Space Ep2",
    ): NasaMedia = NasaMedia(
        nasaId = nasaId,
        title = title,
        description = "The second episode of Webb's journey to space.",
        mediaType = NasaMediaType.VIDEO,
        dateCreated = LocalDate.of(2021, 10, 15),
        center = "GSFC",
        thumbnailUrl = "https://images-assets.nasa.gov/video/Ep2/Ep2~thumb.jpg",
        imageUrl = "https://images-assets.nasa.gov/video/Ep2/Ep2~large.jpg",
    )

    fun audio(
        nasaId: String = "Ep433_Callsign White Flight",
        title: String = "HWHAP Ep433 Callsign White Flight",
    ): NasaMedia = NasaMedia(
        nasaId = nasaId,
        title = title,
        description = "Houston, we have a podcast.",
        mediaType = NasaMediaType.AUDIO,
        dateCreated = LocalDate.of(2026, 8, 14),
        center = "JSC",
    )

    /** [count] distinct images, e.g. to fill a rail. */
    fun images(count: Int, prefix: String = "image"): List<NasaMedia> =
        (0 until count).map { index -> image(nasaId = "$prefix-$index", title = "$prefix #$index") }
}
