package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.moon.LunarLandingSiteRepository
import com.radustavila.nasaexplorer.core.domain.moon.MoonIlluminationCalculator
import com.radustavila.nasaexplorer.core.domain.moon.MoonTilesRepository
import com.radustavila.nasaexplorer.core.model.LunarLandingSite
import com.radustavila.nasaexplorer.core.model.MoonIllumination
import com.radustavila.nasaexplorer.core.model.MoonTilesPack
import com.radustavila.nasaexplorer.core.model.SelenographicPoint
import com.radustavila.nasaexplorer.core.testing.data.TestMoonMap
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Returns [sites] as they are when asked. */
class FakeLunarLandingSiteRepository(
    var sites: List<LunarLandingSite> = TestMoonMap.sites,
) : LunarLandingSiteRepository {
    override suspend fun sites(): List<LunarLandingSite> = sites
}

/** The Sun over [subsolarPoint] whatever the time; records the instants asked for. */
class FakeMoonIlluminationCalculator(
    var subsolarPoint: SelenographicPoint = SelenographicPoint(latitude = 0.0, longitude = 0.0),
) : MoonIlluminationCalculator {
    val instants = mutableListOf<Instant>()

    override fun illumination(instant: Instant): MoonIllumination {
        instants += instant
        return MoonIllumination(instant, subsolarPoint)
    }
}

/** A pack whose state tests set through [pack]; counts downloads and removals. */
class FakeMoonTilesRepository(
    initial: MoonTilesPack = MoonTilesPack.NotDownloaded(sizeBytes = 57_000_000L),
) : MoonTilesRepository {
    val pack = MutableStateFlow(initial)
    var downloads = 0
        private set
    var removals = 0
        private set

    override fun observePack(): Flow<MoonTilesPack> = pack

    override suspend fun download() {
        downloads++
    }

    override suspend fun remove() {
        removals++
    }
}
