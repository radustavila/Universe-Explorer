package com.radustavila.nasaexplorer.core.media.di

import com.radustavila.nasaexplorer.core.domain.podcast.PodcastPlayer
import com.radustavila.nasaexplorer.core.media.Media3PodcastPlayer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface MediaModule {

    @Binds
    fun bindPodcastPlayer(impl: Media3PodcastPlayer): PodcastPlayer
}
