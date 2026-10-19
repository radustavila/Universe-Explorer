package com.radustavila.nasaexplorer.core.database.di

import com.radustavila.nasaexplorer.core.database.repository.RoomFavoritesRepository
import com.radustavila.nasaexplorer.core.domain.repository.FavoritesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface FavoritesModule {

    @Binds
    fun bindFavoritesRepository(impl: RoomFavoritesRepository): FavoritesRepository
}
