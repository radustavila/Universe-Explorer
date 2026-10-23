package com.radustavila.nasaexplorer.feature.moonmap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.radustavila.nasaexplorer.core.domain.moon.MoonTerminator
import com.radustavila.nasaexplorer.core.model.LunarLandingSite
import com.radustavila.nasaexplorer.core.model.MoonIllumination
import com.radustavila.nasaexplorer.core.model.MoonTilesPack
import com.radustavila.nasaexplorer.core.model.SelenographicPoint
import com.radustavila.nasaexplorer.core.testing.data.TestMoonMap
import java.time.Instant

internal const val FAKE_MAP_TAG = "fake-moon-map"

internal object MoonMapFixture {
    /** First quarter: the eastern half of the near side lit. */
    private val firstQuarter = MoonIllumination(Instant.parse("2026-10-23T20:00:00Z"), SelenographicPoint(1.2, 88.0))

    fun state(
        tiles: MoonTilesPack? = MoonTilesPack.NotDownloaded(sizeBytes = 59_768_832L),
        selected: LunarLandingSite? = null,
    ) = MoonMapUiState(
        isLoading = false,
        programmes = TestMoonMap.sites.map { it.programme }.distinct(),
        shownProgrammes = TestMoonMap.sites.map { it.programme }.toSet(),
        sites = TestMoonMap.sites,
        selectedSite = selected,
        illumination = firstQuarter,
        nightSide = MoonTerminator.nightSide(firstQuarter.subsolarPoint),
        tiles = tiles,
    )

    /** A grey stand-in for MapLibre, which Robolectric cannot draw. */
    val map: MoonMapSlot = { _, _, _, _, modifier ->
        Box(modifier.background(Color(0xFF2B2B2B)).testTag(FAKE_MAP_TAG))
    }
}
