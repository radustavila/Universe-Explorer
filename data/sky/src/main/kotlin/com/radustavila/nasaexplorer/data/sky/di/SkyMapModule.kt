package com.radustavila.nasaexplorer.data.sky.di

import com.radustavila.nasaexplorer.core.astro.AstronomyEngineSkyMapEphemeris
import com.radustavila.nasaexplorer.core.domain.sky.DeepSkyCatalogRepository
import com.radustavila.nasaexplorer.core.domain.sky.SkyCatalogRepository
import com.radustavila.nasaexplorer.core.domain.sky.SkyMapEphemeris
import com.radustavila.nasaexplorer.core.domain.sky.SkyMapPreferencesRepository
import com.radustavila.nasaexplorer.core.domain.sky.SkyPositionCalculator
import com.radustavila.nasaexplorer.core.skymap.CelestialSkyPositionCalculator
import com.radustavila.nasaexplorer.core.skymap.ResourceDeepSkyCatalog
import com.radustavila.nasaexplorer.core.skymap.ResourceSkyCatalog
import com.radustavila.nasaexplorer.data.sky.preferences.SkyMapPreferencesDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The sky map's bundled catalogues, its astronomy and its saved choices. Nothing here touches the network. */
@Module
@InstallIn(SingletonComponent::class)
interface SkyMapModule {

    @Binds
    fun bindSkyCatalogRepository(impl: ResourceSkyCatalog): SkyCatalogRepository

    @Binds
    fun bindSkyMapEphemeris(impl: AstronomyEngineSkyMapEphemeris): SkyMapEphemeris

    @Binds
    fun bindSkyMapPreferencesRepository(impl: SkyMapPreferencesDataSource): SkyMapPreferencesRepository

    /** The deep-sky objects (F23), bundled next to the stars. */
    @Binds
    fun bindDeepSkyCatalogRepository(impl: ResourceDeepSkyCatalog): DeepSkyCatalogRepository

    @Binds
    fun bindSkyPositionCalculator(impl: CelestialSkyPositionCalculator): SkyPositionCalculator
}
