package com.radustavila.nasaexplorer.data.moonmap

import com.google.android.play.core.assetpacks.model.AssetPackErrorCode
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import com.radustavila.nasaexplorer.core.model.MoonTilesFailure
import com.radustavila.nasaexplorer.core.model.MoonTilesPack
import com.radustavila.nasaexplorer.data.moonmap.tiles.packFromError
import com.radustavila.nasaexplorer.data.moonmap.tiles.packFromStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MoonTilesPackStatesTest {

    @Test
    fun `a pack Play can deliver but has not offers its size`() {
        assertEquals(
            MoonTilesPack.NotDownloaded(sizeBytes = 57_000_000L),
            packFromStatus(AssetPackStatus.NOT_INSTALLED, AssetPackErrorCode.NO_ERROR, 0, 57_000_000L),
        )
        assertEquals(
            MoonTilesPack.NotDownloaded(sizeBytes = null),
            packFromStatus(AssetPackStatus.UNKNOWN, AssetPackErrorCode.NO_ERROR, 0, 0),
        )
    }

    @Test
    fun `download progress carries the byte counts`() {
        val pack = packFromStatus(AssetPackStatus.DOWNLOADING, AssetPackErrorCode.NO_ERROR, 19_000_000L, 57_000_000L)

        assertEquals(MoonTilesPack.Downloading(19_000_000L, 57_000_000L), pack)
        assertEquals(1f / 3f, (pack as MoonTilesPack.Downloading).fraction!!, 0.001f)
        assertEquals(null, MoonTilesPack.Downloading(0, 0).fraction)
    }

    @Test
    fun `Wi-Fi waits and confirmations read as waiting for Wi-Fi`() {
        assertEquals(MoonTilesPack.WaitingForWifi, packFromStatus(AssetPackStatus.WAITING_FOR_WIFI, 0, 0, 1))
        assertEquals(
            MoonTilesPack.WaitingForWifi,
            packFromStatus(AssetPackStatus.REQUIRES_USER_CONFIRMATION, 0, 0, 1),
        )
    }

    @Test
    fun `installs without Play asset delivery are unavailable, other failures can be retried`() {
        assertEquals(MoonTilesPack.Unavailable, packFromError(AssetPackErrorCode.API_NOT_AVAILABLE))
        assertEquals(MoonTilesPack.Unavailable, packFromError(AssetPackErrorCode.APP_NOT_OWNED))
        assertEquals(
            MoonTilesPack.Failed(MoonTilesFailure.NO_CONNECTION),
            packFromError(AssetPackErrorCode.NETWORK_ERROR),
        )
        assertEquals(
            MoonTilesPack.Failed(MoonTilesFailure.NOT_ENOUGH_STORAGE),
            packFromStatus(AssetPackStatus.FAILED, AssetPackErrorCode.INSUFFICIENT_STORAGE, 0, 1),
        )
        assertEquals(MoonTilesPack.Failed(MoonTilesFailure.OTHER), packFromError(AssetPackErrorCode.INTERNAL_ERROR))
    }
}
