package com.radustavila.nasaexplorer.data.moonmap.tiles

import com.google.android.play.core.assetpacks.model.AssetPackErrorCode
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import com.radustavila.nasaexplorer.core.model.MoonTilesFailure
import com.radustavila.nasaexplorer.core.model.MoonTilesPack

/**
 * Play's view of the pack turned into the app's. Installed packs are recognised by their location,
 * not by status, so a completed status alone (location not yet readable) still reads as
 * downloading until the location appears.
 */
internal fun packFromStatus(status: Int, errorCode: Int, downloadedBytes: Long, totalBytes: Long): MoonTilesPack =
    when (status) {
        AssetPackStatus.PENDING,
        AssetPackStatus.DOWNLOADING,
        AssetPackStatus.TRANSFERRING,
        AssetPackStatus.COMPLETED,
        -> MoonTilesPack.Downloading(downloadedBytes, totalBytes)
        AssetPackStatus.WAITING_FOR_WIFI,
        AssetPackStatus.REQUIRES_USER_CONFIRMATION,
        -> MoonTilesPack.WaitingForWifi
        AssetPackStatus.FAILED -> packFromError(errorCode)
        else -> MoonTilesPack.NotDownloaded(totalBytes.takeIf { it > 0 })
    }

/** An error from the store: no Play delivery for this install, or a download that can be retried. */
internal fun packFromError(errorCode: Int): MoonTilesPack = when (errorCode) {
    AssetPackErrorCode.APP_UNAVAILABLE,
    AssetPackErrorCode.PACK_UNAVAILABLE,
    AssetPackErrorCode.API_NOT_AVAILABLE,
    AssetPackErrorCode.APP_NOT_OWNED,
    AssetPackErrorCode.UNRECOGNIZED_INSTALLATION,
    AssetPackErrorCode.ACCESS_DENIED,
    -> MoonTilesPack.Unavailable
    AssetPackErrorCode.NETWORK_ERROR -> MoonTilesPack.Failed(MoonTilesFailure.NO_CONNECTION)
    AssetPackErrorCode.INSUFFICIENT_STORAGE -> MoonTilesPack.Failed(MoonTilesFailure.NOT_ENOUGH_STORAGE)
    else -> MoonTilesPack.Failed(MoonTilesFailure.OTHER)
}
