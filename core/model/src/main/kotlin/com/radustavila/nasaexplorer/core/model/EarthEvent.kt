package com.radustavila.nasaexplorer.core.model

import java.time.Instant

/**
 * A natural event tracked by NASA EONET: a wildfire, a storm, an iceberg, an eruption...
 *
 * [geometry] holds every reported position, oldest first, and is never empty: a storm's list is its
 * track, a wildfire usually has a single entry.
 */
data class EarthEvent(
    val id: String,
    val title: String,
    val category: EarthEventCategory,
    val sources: List<EarthEventSource>,
    val geometry: List<EarthEventGeometry>,
    /** When EONET closed the event; `null` while it is still going on. */
    val closedAt: Instant? = null,
) {
    init {
        require(geometry.isNotEmpty()) { "event $id has no geometry" }
    }

    /** The most recent report: where the event is now and how strong. */
    val latest: EarthEventGeometry get() = geometry.last()

    val lastUpdate: Instant get() = latest.date

    /** The reported positions as a path, oldest first; only meaningful with two or more points. */
    val track: List<GeoCoordinate>
        get() = geometry.mapNotNull { (it.shape as? EarthEventShape.Point)?.position }
}

/**
 * EONET's thirteen event categories. [key] is EONET's category id, as stored in the cache; never
 * rename one.
 */
enum class EarthEventCategory(val key: String) {
    DROUGHT("drought"),
    DUST_HAZE("dustHaze"),
    EARTHQUAKES("earthquakes"),
    FLOODS("floods"),
    LANDSLIDES("landslides"),
    MANMADE("manmade"),
    SEA_LAKE_ICE("seaLakeIce"),
    SEVERE_STORMS("severeStorms"),
    SNOW("snow"),
    TEMP_EXTREMES("tempExtremes"),
    VOLCANOES("volcanoes"),
    WATER_COLOR("waterColor"),
    WILDFIRES("wildfires"),
    ;

    companion object {
        fun fromKey(key: String): EarthEventCategory? = entries.firstOrNull { it.key == key }
    }
}

/** Where EONET learned about an event, e.g. JTWC for a typhoon; [url] is that agency's page. */
data class EarthEventSource(val id: String, val url: String)

/** One report of an event: when, where, and (for some sources) how strong. */
data class EarthEventGeometry(
    val date: Instant,
    val shape: EarthEventShape,
    val magnitude: EventMagnitude? = null,
)

sealed interface EarthEventShape {
    /** A representative position for the shape, e.g. where to put its map marker. */
    val center: GeoCoordinate

    data class Point(val position: GeoCoordinate) : EarthEventShape {
        override val center: GeoCoordinate get() = position
    }

    /** An area: the first ring is the outline, any others are holes. */
    data class Polygon(val rings: List<List<GeoCoordinate>>) : EarthEventShape {
        init {
            require(rings.isNotEmpty() && rings.first().isNotEmpty()) { "a polygon needs an outline" }
        }

        /** Centre of the outline's bounding box: good enough to place a marker on a small area. */
        override val center: GeoCoordinate
            get() {
                val outline = rings.first()
                return GeoCoordinate(
                    latitude = (outline.minOf { it.latitude } + outline.maxOf { it.latitude }) / 2,
                    longitude = (outline.minOf { it.longitude } + outline.maxOf { it.longitude }) / 2,
                )
            }
    }
}

/** A WGS 84 position. */
data class GeoCoordinate(val latitude: Double, val longitude: Double)

/** A size or strength as the source reports it, e.g. 55 kts of wind or 751 acres burned. */
data class EventMagnitude(val value: Double, val unit: String)

/**
 * A NASA GIBS imagery layer that shows an event category well, already translated to the Web
 * Mercator tiles the app's map draws.
 */
data class EventImageryLayer(
    /** GIBS layer identifier, e.g. `MODIS_Terra_CorrectedReflectance_TrueColor`. */
    val id: String,
    /** Web Mercator tile matrix set, e.g. `GoogleMapsCompatible_Level9`. */
    val tileMatrixSet: String,
    /** Tile file extension: `jpg` or `png`. */
    val fileExtension: String,
)
