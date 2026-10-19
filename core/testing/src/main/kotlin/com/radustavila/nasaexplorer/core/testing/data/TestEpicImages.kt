package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.EpicCollection
import com.radustavila.nasaexplorer.core.model.EpicImage
import com.radustavila.nasaexplorer.core.model.GeoPoint
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

object TestEpicImages {

    val LATEST_DATE: LocalDate = LocalDate.of(2026, 10, 16)

    fun image(
        date: LocalDate = LATEST_DATE,
        index: Int = 0,
        collection: EpicCollection = EpicCollection.NATURAL,
    ): EpicImage {
        val capturedAt = date.atStartOfDay(ZoneOffset.UTC).toInstant()
            .plus(Duration.ofMinutes(22L + index * MINUTES_BETWEEN_CAPTURES))
        val stamp = "%s%02d".format(date.toString().replace("-", ""), index)
        val prefix = if (collection == EpicCollection.NATURAL) "epic_1b" else "epic_RGB"
        val directory = "https://epic.gsfc.nasa.gov/archive/${collection.apiValue}/" +
            date.toString().replace('-', '/')
        return EpicImage(
            identifier = stamp,
            collection = collection,
            capturedAt = capturedAt,
            caption = "This image was taken by NASA's EPIC camera onboard the NOAA DSCOVR spacecraft",
            centroid = GeoPoint(latitude = 0.66, longitude = 163.7 - index * DEGREES_PER_CAPTURE),
            thumbnailUrl = "$directory/thumbs/${prefix}_$stamp.jpg",
            imageUrl = "$directory/jpg/${prefix}_$stamp.jpg",
        )
    }

    fun day(
        date: LocalDate = LATEST_DATE,
        size: Int = 3,
        collection: EpicCollection = EpicCollection.NATURAL,
    ): List<EpicImage> = List(size) { index -> image(date = date, index = index, collection = collection) }

    private const val MINUTES_BETWEEN_CAPTURES = 108L
    private const val DEGREES_PER_CAPTURE = 27.0
}
