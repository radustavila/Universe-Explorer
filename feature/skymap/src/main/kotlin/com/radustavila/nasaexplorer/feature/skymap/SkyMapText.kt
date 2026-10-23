package com.radustavila.nasaexplorer.feature.skymap

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import com.radustavila.nasaexplorer.core.model.OrbitalElements
import com.radustavila.nasaexplorer.core.model.SatelliteSet
import com.radustavila.nasaexplorer.core.model.SkyCatalog
import com.radustavila.nasaexplorer.core.model.SkyMapBodies
import com.radustavila.nasaexplorer.core.model.SolarSystemBody
import com.radustavila.nasaexplorer.feature.skymap.render.SkyNames
import com.radustavila.nasaexplorer.feature.skymap.render.satellite
import java.text.Normalizer

@StringRes
internal fun SolarSystemBody.nameRes(): Int = when (this) {
    SolarSystemBody.SUN -> R.string.sky_map_sun
    SolarSystemBody.MOON -> R.string.sky_map_moon
    SolarSystemBody.MERCURY -> R.string.sky_map_mercury
    SolarSystemBody.VENUS -> R.string.sky_map_venus
    SolarSystemBody.MARS -> R.string.sky_map_mars
    SolarSystemBody.JUPITER -> R.string.sky_map_jupiter
    SolarSystemBody.SATURN -> R.string.sky_map_saturn
    SolarSystemBody.URANUS -> R.string.sky_map_uranus
    SolarSystemBody.NEPTUNE -> R.string.sky_map_neptune
}

@StringRes
internal fun SolarSystemBody.kindRes(): Int = when (this) {
    SolarSystemBody.SUN -> R.string.sky_map_kind_our_star
    SolarSystemBody.MOON -> R.string.sky_map_kind_moon
    else -> R.string.sky_map_kind_planet
}

private val COMPASS = intArrayOf(
    R.string.sky_map_compass_n,
    R.string.sky_map_compass_ne,
    R.string.sky_map_compass_e,
    R.string.sky_map_compass_se,
    R.string.sky_map_compass_s,
    R.string.sky_map_compass_sw,
    R.string.sky_map_compass_w,
    R.string.sky_map_compass_nw,
)

private val CARDINALS = intArrayOf(
    R.string.sky_map_cardinal_n,
    R.string.sky_map_cardinal_ne,
    R.string.sky_map_cardinal_e,
    R.string.sky_map_cardinal_se,
    R.string.sky_map_cardinal_s,
    R.string.sky_map_cardinal_sw,
    R.string.sky_map_cardinal_w,
    R.string.sky_map_cardinal_nw,
)

private val HUBBLE_NORAD_ID = checkNotNull(SatelliteSet.HUBBLE.noradId)
private val TIANGONG_NORAD_ID = checkNotNull(SatelliteSet.TIANGONG.noradId)

/** Everyday names of the stations F15 tracks one by one, by NORAD id. */
private val STATION_NAMES = mapOf(
    OrbitalElements.ISS_NORAD_ID to R.string.sky_map_iss,
    HUBBLE_NORAD_ID to R.string.sky_map_hubble,
    TIANGONG_NORAD_ID to R.string.sky_map_tiangong,
)

@StringRes
internal fun satelliteKindRes(noradId: Int): Int = when (noradId) {
    OrbitalElements.ISS_NORAD_ID -> R.string.sky_map_kind_station
    HUBBLE_NORAD_ID -> R.string.sky_map_kind_telescope
    TIANGONG_NORAD_ID -> R.string.sky_map_kind_chinese_station
    else -> R.string.sky_map_kind_satellite
}

private const val COMPASS_SECTOR = 45.0
private const val FULL_TURN = 360.0

/** "north-east" for an azimuth near 45 degrees. */
@StringRes
internal fun compassPointRes(azimuth: Double): Int =
    COMPASS[((azimuth.mod(FULL_TURN) + COMPASS_SECTOR / 2) / COMPASS_SECTOR).toInt() % COMPASS.size]

/** The names the renderer draws, resolved once per configuration. */
@Composable
internal fun rememberSkyNames(): SkyNames {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    return remember(resources, configuration) {
        SkyNames(
            bodies = SolarSystemBody.entries.associateWith { resources.getString(it.nameRes()) },
            satellites = STATION_NAMES.mapValues { (_, name) -> resources.getString(name) },
            cardinals = CARDINALS.map(resources::getString),
            galacticCentre = resources.getString(R.string.sky_map_galactic_centre),
            milkyWayCore = resources.getString(R.string.sky_map_milky_way_core),
        )
    }
}

/** One match of a sky search. */
internal data class SkySearchResult(val target: SkyTarget, val name: String)

/**
 * Everything named on the map that matches [query], ignoring case and accents: the Sun, Moon and
 * planets, the ISS, comets (by full name), the galactic centre (also as the Milky Way's core), constellations (by
 * name or abbreviation) and named stars, brightest first within each group; names starting with the query come first.
 */
internal fun searchSky(
    query: String,
    catalog: SkyCatalog,
    bodies: SkyMapBodies?,
    names: SkyNames,
    limit: Int = MAX_RESULTS,
): List<SkySearchResult> {
    val needle = query.foldForSearch()
    if (needle.isEmpty()) return emptyList()
    val candidates = buildList {
        SolarSystemBody.entries.forEach { body ->
            names.bodies[body]?.let { add(SkySearchResult(SkyTarget.Body(body), it)) }
        }
        bodies?.satellites?.forEach {
            add(SkySearchResult(SkyTarget.Satellite(it.noradId), names.satellite(it.noradId, bodies)))
        }
        bodies?.comets?.forEach { add(SkySearchResult(SkyTarget.Comet(it.designation), it.name)) }
        if (names.galacticCentre.isNotEmpty()) add(SkySearchResult(SkyTarget.GalacticCentre, names.galacticCentre))
        if (names.milkyWayCore.isNotEmpty()) add(SkySearchResult(SkyTarget.GalacticCentre, names.milkyWayCore))
        val seen = mutableSetOf<String>()
        catalog.constellations.forEachIndexed { index, figure ->
            if (seen.add(figure.id)) add(SkySearchResult(SkyTarget.Constellation(index), figure.name))
        }
        catalog.stars.forEachIndexed { index, star ->
            star.name?.let { add(SkySearchResult(SkyTarget.StarTarget(index), it)) }
        }
    }
    val abbreviations = catalog.constellations.withIndex()
        .associate { (index, figure) -> index to figure.id.foldForSearch() }
    val matches = candidates.filter { result ->
        result.name.foldForSearch().contains(needle) ||
            (result.target as? SkyTarget.Constellation)?.let { abbreviations[it.index] == needle } == true
    }
    return matches.sortedBy { if (it.name.foldForSearch().startsWith(needle)) 0 else 1 }
        .distinctBy { it.target }
        .take(limit)
}

private const val MAX_RESULTS = 8
private val COMBINING_MARKS = Regex("\\p{Mn}+")

private fun String.foldForSearch(): String =
    COMBINING_MARKS.replace(Normalizer.normalize(trim(), Normalizer.Form.NFD), "").lowercase()
