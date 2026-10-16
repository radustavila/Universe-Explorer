package com.radustavila.nasaexplorer.feature.widget.di

import com.radustavila.nasaexplorer.core.domain.announcement.LatestApodWidgetUpdater
import com.radustavila.nasaexplorer.feature.widget.GlanceLatestApodWidgetUpdater
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface WidgetModule {

    @Binds
    fun bindLatestApodWidgetUpdater(impl: GlanceLatestApodWidgetUpdater): LatestApodWidgetUpdater
}
