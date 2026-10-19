package com.radustavila.nasaexplorer.core.domain.wallpaper

import com.radustavila.nasaexplorer.core.model.Apod

/**
 * Makes an entry's picture the device's home screen wallpaper. The domain decides *which* entry and
 * *when*; the implementation only decides how.
 */
interface WallpaperSetter {

    /** @return whether the wallpaper was actually changed. */
    suspend fun setWallpaper(apod: Apod): Boolean
}
