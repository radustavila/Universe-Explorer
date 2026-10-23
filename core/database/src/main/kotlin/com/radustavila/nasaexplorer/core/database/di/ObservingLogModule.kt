package com.radustavila.nasaexplorer.core.database.di

import com.radustavila.nasaexplorer.core.database.repository.RoomObservingLogRepository
import com.radustavila.nasaexplorer.core.domain.sky.ObservingLogRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The observing log (F23) lives in the favourites database, so backups keep it too. */
@Module
@InstallIn(SingletonComponent::class)
interface ObservingLogModule {

    @Binds
    fun bindObservingLogRepository(impl: RoomObservingLogRepository): ObservingLogRepository
}
