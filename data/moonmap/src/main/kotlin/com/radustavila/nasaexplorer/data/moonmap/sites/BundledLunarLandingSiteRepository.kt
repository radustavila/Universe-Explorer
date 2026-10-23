package com.radustavila.nasaexplorer.data.moonmap.sites

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.moon.LunarLandingSiteRepository
import com.radustavila.nasaexplorer.core.model.LunarLandingKind
import com.radustavila.nasaexplorer.core.model.LunarLandingSite
import com.radustavila.nasaexplorer.core.model.LunarProgramme
import com.radustavila.nasaexplorer.core.model.SelenographicPoint
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The landing sites from `lunar_landing_sites.json` (a Java resource next to this class's module):
 * NSSDCA's LRO-derived Apollo positions, its Luna and Surveyor tables, and later landers added by
 * hand. Read once, then kept in memory.
 */
@Singleton
internal class BundledLunarLandingSiteRepository @Inject constructor(
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : LunarLandingSiteRepository {

    private val mutex = Mutex()
    private var cached: List<LunarLandingSite>? = null

    override suspend fun sites(): List<LunarLandingSite> = mutex.withLock {
        cached ?: withContext(ioDispatcher) { readSites() }.also { cached = it }
    }

    private fun readSites(): List<LunarLandingSite> {
        val text = checkNotNull(javaClass.getResourceAsStream(RESOURCE)) { "$RESOURCE is missing" }
            .bufferedReader().use { it.readText() }
        return parseSites(text)
    }

    companion object {
        const val RESOURCE = "/lunar_landing_sites.json"

        private val json = Json { ignoreUnknownKeys = true }

        /** Sites oldest first; entries with an unknown programme or kind are dropped. */
        internal fun parseSites(text: String): List<LunarLandingSite> =
            json.decodeFromString<SiteTable>(text).sites
                .mapNotNull(SiteRow::toSite)
                .sortedBy(LunarLandingSite::landedOn)
    }
}

@Serializable
private data class SiteTable(val sites: List<SiteRow>)

@Serializable
private data class SiteRow(
    val id: String,
    val mission: String,
    val programme: String,
    val kind: String,
    val landed: String,
    val latitude: Double,
    val longitude: Double,
    val region: String,
    val crew: List<String> = emptyList(),
    val rover: String? = null,
    val nssdcaId: String? = null,
) {
    fun toSite(): LunarLandingSite? {
        val programme = LunarProgramme.entries.firstOrNull { it.name == programme }
        val kind = LunarLandingKind.entries.firstOrNull { it.name == kind }
        val onTheMoon = latitude in LATITUDES && longitude in LONGITUDES
        if (programme == null || kind == null || !onTheMoon) return null
        return LunarLandingSite(
            id = id,
            mission = mission,
            programme = programme,
            kind = kind,
            landedOn = LocalDate.parse(landed),
            position = SelenographicPoint(latitude, longitude),
            region = region,
            crew = crew,
            rover = rover,
            nssdcaId = nssdcaId,
        )
    }
}

private val LATITUDES = -90.0..90.0
private val LONGITUDES = -180.0..180.0
