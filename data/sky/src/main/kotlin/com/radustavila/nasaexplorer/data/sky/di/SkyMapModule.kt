package com.radustavila.nasaexplorer.data.sky.di

import com.radustavila.nasaexplorer.core.domain.sky.SkyCatalogRepository
import com.radustavila.nasaexplorer.core.domain.sky.SkyMapPreferencesRepository
import com.radustavila.nasaexplorer.core.skymap.ResourceSkyCatalog
import com.radustavila.nasaexplorer.data.sky.preferences.SkyMapPreferencesDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The sky map's bundled catalogue and its saved choices. Nothing here touches the network. */
@Module
@InstallIn(SingletonComponent::class)
interface SkyMapModule {

    @Binds
    fun bindSkyCatalogRepository(impl: ResourceSkyCatalog): SkyCatalogRepository

    @Binds
    fun bindSkyMapPreferencesRepository(impl: SkyMapPreferencesDataSource): SkyMapPreferencesRepository
}
