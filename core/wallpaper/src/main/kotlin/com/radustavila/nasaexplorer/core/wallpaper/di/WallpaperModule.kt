package com.radustavila.nasaexplorer.core.wallpaper.di

import com.radustavila.nasaexplorer.core.domain.wallpaper.WallpaperSetter
import com.radustavila.nasaexplorer.core.wallpaper.SystemWallpaperSetter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface WallpaperModule {

    @Binds
    fun bindWallpaperSetter(impl: SystemWallpaperSetter): WallpaperSetter
}
