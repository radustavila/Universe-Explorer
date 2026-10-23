package com.radustavila.nasaexplorer.data.moonmap.di

import com.radustavila.nasaexplorer.core.astro.AstronomyEngineMoonIlluminationCalculator
import com.radustavila.nasaexplorer.core.domain.moon.LunarLandingSiteRepository
import com.radustavila.nasaexplorer.core.domain.moon.MoonIlluminationCalculator
import com.radustavila.nasaexplorer.core.domain.moon.MoonTilesRepository
import com.radustavila.nasaexplorer.core.domain.quiz.QuizQuestionSource
import com.radustavila.nasaexplorer.core.domain.storage.StorageUsageSource
import com.radustavila.nasaexplorer.data.moonmap.quiz.LandingSiteQuizSource
import com.radustavila.nasaexplorer.data.moonmap.sites.BundledLunarLandingSiteRepository
import com.radustavila.nasaexplorer.data.moonmap.tiles.PlayMoonTilesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal interface MoonMapDataModule {

    @Binds
    fun bindLunarLandingSiteRepository(impl: BundledLunarLandingSiteRepository): LunarLandingSiteRepository

    @Binds
    fun bindMoonIlluminationCalculator(impl: AstronomyEngineMoonIlluminationCalculator): MoonIlluminationCalculator

    @Binds
    fun bindMoonTilesRepository(impl: PlayMoonTilesRepository): MoonTilesRepository

    @Binds
    @IntoSet
    fun bindMoonTilesStorageUsage(impl: PlayMoonTilesRepository): StorageUsageSource

    @Binds
    @IntoSet
    fun bindLandingSiteQuizSource(impl: LandingSiteQuizSource): QuizQuestionSource
}
