package com.radustavila.nasaexplorer.feature.moonmap

import androidx.annotation.StringRes
import com.radustavila.nasaexplorer.core.model.LunarLandingKind
import com.radustavila.nasaexplorer.core.model.LunarProgramme
import com.radustavila.nasaexplorer.core.model.MoonTilesFailure

@get:StringRes
internal val LunarProgramme.labelRes: Int
    get() = when (this) {
        LunarProgramme.APOLLO -> R.string.moon_map_programme_apollo
        LunarProgramme.LUNA -> R.string.moon_map_programme_luna
        LunarProgramme.SURVEYOR -> R.string.moon_map_programme_surveyor
        LunarProgramme.CHANGE -> R.string.moon_map_programme_change
        LunarProgramme.CHANDRAYAAN -> R.string.moon_map_programme_chandrayaan
        LunarProgramme.SLIM -> R.string.moon_map_programme_slim
        LunarProgramme.CLPS -> R.string.moon_map_programme_clps
    }

@get:StringRes
internal val LunarLandingKind.labelRes: Int
    get() = when (this) {
        LunarLandingKind.CREWED -> R.string.moon_map_kind_crewed
        LunarLandingKind.LANDER -> R.string.moon_map_kind_lander
        LunarLandingKind.ROVER -> R.string.moon_map_kind_rover
        LunarLandingKind.SAMPLE_RETURN -> R.string.moon_map_kind_sample_return
    }

@get:StringRes
internal val MoonTilesFailure.labelRes: Int
    get() = when (this) {
        MoonTilesFailure.NO_CONNECTION -> R.string.moon_map_tiles_failed_connection
        MoonTilesFailure.NOT_ENOUGH_STORAGE -> R.string.moon_map_tiles_failed_storage
        MoonTilesFailure.OTHER -> R.string.moon_map_tiles_failed_other
    }
