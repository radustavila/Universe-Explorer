package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.wallpaper.WallpaperSetter
import com.radustavila.nasaexplorer.core.model.Apod

class FakeWallpaperSetter(
    /** What [setWallpaper] reports; `false` stands in for a picture that could not be loaded. */
    var succeeds: Boolean = true,
) : WallpaperSetter {

    var applied: List<Apod> = emptyList()
        private set

    override suspend fun setWallpaper(apod: Apod): Boolean {
        if (succeeds) applied = applied + apod
        return succeeds
    }
}
