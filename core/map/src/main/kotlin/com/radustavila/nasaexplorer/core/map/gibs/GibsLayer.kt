package com.radustavila.nasaexplorer.core.map.gibs

import java.time.LocalDate

/**
 * One NASA GIBS imagery layer served over WMTS in Web Mercator (EPSG:3857).
 *
 * A data class rather than an enum so features can show any of the ~1,000 GIBS layers: F9 builds
 * one from the layer name, matrix set and format that EONET's `/layers/{category}` lists. The
 * presets in the companion are the layers the satellite map uses.
 */
data class GibsLayer(
    /** WMTS layer identifier, e.g. `VIIRS_SNPP_CorrectedReflectance_TrueColor`. */
    val id: String,
    val matrixSet: GibsTileMatrixSet,
    /** File extension of a tile: `jpg`, `jpeg` or `png`, exactly as GIBS spells it for the layer. */
    val format: String,
    val time: GibsTime,
) {
    /** Deepest zoom level GIBS serves; MapLibre overzooms past it instead of requesting 400s. */
    val maxZoom: Int get() = matrixSet.maxZoom

    /** Raster tile URL with MapLibre's `{z}/{y}/{x}` placeholders, for the given day. */
    fun tileUrlTemplate(date: LocalDate?): String = buildString {
        append(GIBS_WMTS_BASE_URL)
        append('/').append(id).append("/default")
        timeSegment(date)?.let { append('/').append(it) }
        append('/').append(matrixSet.id)
        append("/{z}/{y}/{x}.").append(format)
    }

    /** Concrete tile URL; mostly useful in tests and diagnostics. */
    fun tileUrl(date: LocalDate?, zoom: Int, x: Int, y: Int): String =
        tileUrlTemplate(date)
            .replace("{z}", zoom.toString())
            .replace("{y}", y.toString())
            .replace("{x}", x.toString())

    private fun timeSegment(date: LocalDate?): String? = when (time) {
        is GibsTime.Daily -> requireNotNull(date) { "$id is a daily layer and needs a date" }.toString()
        is GibsTime.Fixed -> time.date.toString()
        GibsTime.None -> null
    }

    companion object {
        /** Daily true colour from Suomi NPP. The map's default. */
        val ViirsSnppTrueColor = GibsLayer(
            id = "VIIRS_SNPP_CorrectedReflectance_TrueColor",
            matrixSet = GibsTileMatrixSet.GoogleMapsCompatibleLevel9,
            format = "jpg",
            time = GibsTime.Daily(firstDate = LocalDate.of(2015, 11, 24)),
        )

        /** Daily true colour from NOAA-20, a second pass that fills Suomi NPP's swath gaps. */
        val ViirsNoaa20TrueColor = GibsLayer(
            id = "VIIRS_NOAA20_CorrectedReflectance_TrueColor",
            matrixSet = GibsTileMatrixSet.GoogleMapsCompatibleLevel9,
            format = "jpg",
            time = GibsTime.Daily(firstDate = LocalDate.of(2018, 1, 1)),
        )

        /** Daily true colour from Terra MODIS; its archive reaches back to 2000. */
        val ModisTerraTrueColor = GibsLayer(
            id = "MODIS_Terra_CorrectedReflectance_TrueColor",
            matrixSet = GibsTileMatrixSet.GoogleMapsCompatibleLevel9,
            format = "jpg",
            time = GibsTime.Daily(firstDate = LocalDate.of(2000, 2, 24)),
        )

        /** City lights at night, a single 2016 composite. */
        val ViirsBlackMarble = GibsLayer(
            id = "VIIRS_Black_Marble",
            matrixSet = GibsTileMatrixSet.GoogleMapsCompatibleLevel8,
            format = "png",
            time = GibsTime.Fixed(LocalDate.of(2016, 1, 1)),
        )

        /** Cloud-free shaded relief and bathymetry: the base drawn under every other layer. */
        val BlueMarble = GibsLayer(
            id = "BlueMarble_ShadedRelief_Bathymetry",
            matrixSet = GibsTileMatrixSet.GoogleMapsCompatibleLevel8,
            format = "jpeg",
            time = GibsTime.None,
        )

        /**
         * The true colour layer that has imagery for [date]: Suomi NPP from its first day, Terra
         * MODIS before that.
         */
        fun trueColorFor(date: LocalDate): GibsLayer {
            val snppStart = (ViirsSnppTrueColor.time as GibsTime.Daily).firstDate
            return if (snppStart == null || date >= snppStart) ViirsSnppTrueColor else ModisTerraTrueColor
        }
    }
}

/** The WMTS tile matrix sets GIBS offers in Web Mercator, named by their deepest zoom level. */
enum class GibsTileMatrixSet(val id: String, val maxZoom: Int) {
    GoogleMapsCompatibleLevel6("GoogleMapsCompatible_Level6", 6),
    GoogleMapsCompatibleLevel7("GoogleMapsCompatible_Level7", 7),
    GoogleMapsCompatibleLevel8("GoogleMapsCompatible_Level8", 8),
    GoogleMapsCompatibleLevel9("GoogleMapsCompatible_Level9", 9),
    GoogleMapsCompatibleLevel12("GoogleMapsCompatible_Level12", 12),
    GoogleMapsCompatibleLevel13("GoogleMapsCompatible_Level13", 13),
    ;

    companion object {
        fun fromId(id: String): GibsTileMatrixSet? = entries.firstOrNull { it.id == id }
    }
}

/** How a layer's tile URL encodes time. */
sealed interface GibsTime {
    /** One image per day; the URL carries the requested date. */
    data class Daily(val firstDate: LocalDate? = null) : GibsTime

    /** A single composite published against one date, whatever day is selected. */
    data class Fixed(val date: LocalDate) : GibsTime

    /** Timeless layer; the URL has no date segment. */
    data object None : GibsTime
}

internal const val GIBS_HOST = "gibs.earthdata.nasa.gov"
internal const val GIBS_WMTS_BASE_URL = "https://$GIBS_HOST/wmts/epsg3857/best"
