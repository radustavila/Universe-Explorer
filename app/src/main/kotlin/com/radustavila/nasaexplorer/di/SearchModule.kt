package com.radustavila.nasaexplorer.di

import com.radustavila.nasaexplorer.core.domain.search.SearchableSource
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Declares the set every data module adds its [SearchableSource] to, so the graph still builds when
 * none does (e.g. a test that swaps the data modules out).
 */
@Module
@InstallIn(SingletonComponent::class)
interface SearchModule {

    @Multibinds
    fun searchableSources(): Set<SearchableSource>
}
