package com.radustavila.nasaexplorer.core.domain.moon

import com.radustavila.nasaexplorer.core.model.LunarLandingSite
import com.radustavila.nasaexplorer.core.model.MoonIllumination
import com.radustavila.nasaexplorer.core.model.MoonTilesPack
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** The landing sites on the Moon map (F25), from a table bundled with the app. */
interface LunarLandingSiteRepository {
    /** Every site, oldest landing first. */
    suspend fun sites(): List<LunarLandingSite>
}

/** Where the Sun stands over the Moon, computed on the device. */
interface MoonIlluminationCalculator {
    fun illumination(instant: Instant): MoonIllumination
}

/**
 * The on-demand pack with the Moon map's zoom 5–6 tiles. Downloads only when asked; the map works
 * without it at zoom 0–4.
 */
interface MoonTilesRepository {
    /** The pack's state, updated while it downloads. */
    fun observePack(): Flow<MoonTilesPack>

    /** Asks the store to download the pack. Progress arrives through [observePack]. */
    suspend fun download()

    /** Deletes the pack from the device. */
    suspend fun remove()
}
