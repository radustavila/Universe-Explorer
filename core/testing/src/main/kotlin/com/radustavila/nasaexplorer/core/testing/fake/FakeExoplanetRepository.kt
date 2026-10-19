package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.repository.ExoplanetRepository
import com.radustavila.nasaexplorer.core.model.Exoplanet
import com.radustavila.nasaexplorer.core.model.ExoplanetCatalogStats
import com.radustavila.nasaexplorer.core.model.ExoplanetList
import com.radustavila.nasaexplorer.core.model.ExoplanetUnits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [ExoplanetRepository]. Lists are derived from the cached planets with the same rules
 * the Room queries use, so a test seeds planets once and reads any list.
 */
class FakeExoplanetRepository(
    initialPlanets: List<Exoplanet> = emptyList(),
    /** Planets discovered in this year or later count as "newest". */
    var newestSinceYear: Int = 2025,
) : ExoplanetRepository {

    private val planets = MutableStateFlow(initialPlanets.associateBy(Exoplanet::name))
    private val stats = MutableStateFlow<ExoplanetCatalogStats?>(null)

    /** What the next [refresh] returns; a success stores [planetsOnRefresh]. */
    var refreshResult: DataResult<Int>? = null
    var planetsOnRefresh: List<Exoplanet> = emptyList()
    var refreshCallCount: Int = 0
        private set

    fun setPlanets(value: List<Exoplanet>) {
        planets.value = value.associateBy(Exoplanet::name)
    }

    fun setStats(value: ExoplanetCatalogStats?) {
        stats.value = value
    }

    override fun observeList(list: ExoplanetList): Flow<List<Exoplanet>> = planets.map { byName ->
        val all = byName.values
        when (list) {
            ExoplanetList.NEWEST -> all.filter { it.discoveryYear >= newestSinceYear }
                .sortedWith(
                    compareByDescending<Exoplanet> { it.discoveryPublished }
                        .thenByDescending { it.discoveryYear }
                        .thenBy { it.name },
                )

            ExoplanetList.NEAREST -> all.filter { it.distanceParsecs != null }
                .sortedWith(compareBy<Exoplanet> { it.distanceParsecs }.thenBy { it.name })

            ExoplanetList.EARTH_SIZED -> all
                .filter { radius -> radius.radiusEarth?.let { it in ExoplanetUnits.EARTH_SIZED_RADII } == true }
                .sortedWith(compareBy<Exoplanet, Double?>(nullsLast()) { it.distanceParsecs }.thenBy { it.name })
        }
    }

    override fun observeExoplanet(name: String): Flow<Exoplanet?> = planets.map { it[name] }

    override fun observeCatalogStats(): Flow<ExoplanetCatalogStats?> = stats

    override suspend fun refresh(): DataResult<Int> {
        refreshCallCount++
        val result = refreshResult ?: DataResult.Success(planetsOnRefresh.size)
        if (result is DataResult.Success) {
            planets.value = planets.value + planetsOnRefresh.associateBy(Exoplanet::name)
        }
        return result
    }
}
