package com.radustavila.nasaexplorer.feature.events.di

import com.radustavila.nasaexplorer.core.domain.search.SearchableSource
import com.radustavila.nasaexplorer.feature.events.search.SkyEventsSearchableSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Sky events join universal search from here rather than from a data module: there is no stored
 * data to search, and the event names are this feature's words (see [SkyEventsSearchableSource]).
 */
@Module
@InstallIn(SingletonComponent::class)
interface SkyEventsSearchModule {

    @Binds
    @IntoSet
    fun bindSkyEventsSearchableSource(impl: SkyEventsSearchableSource): SearchableSource
}
