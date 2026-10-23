package com.radustavila.nasaexplorer.data.moonmap.tiles

import android.content.Context
import com.google.android.play.core.assetpacks.AssetPackException
import com.google.android.play.core.assetpacks.AssetPackManager
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackState
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.model.AssetPackErrorCode
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.moon.MoonTilesRepository
import com.radustavila.nasaexplorer.core.domain.storage.StorageEntry
import com.radustavila.nasaexplorer.core.domain.storage.StorageKind
import com.radustavila.nasaexplorer.core.domain.storage.StorageUsageSource
import com.radustavila.nasaexplorer.core.model.MoonTilesPack
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import com.google.android.gms.tasks.Task

/**
 * The `moon_tiles_deep` on-demand asset pack (module `:moontiles`) through Play Asset Delivery.
 * Installs that did not come from Play (a debug APK pushed with adb) have no asset delivery and
 * read as [MoonTilesPack.Unavailable]; the map then stops at its bundled zoom 4.
 */
@Singleton
internal class PlayMoonTilesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : MoonTilesRepository, StorageUsageSource {

    private val manager: AssetPackManager by lazy { AssetPackManagerFactory.getInstance(context) }

    /** Fired after a fetch or removal fails or finishes, so observers read the pack again. */
    private val changes = MutableSharedFlow<MoonTilesPack?>(extraBufferCapacity = 1)

    override fun observePack(): Flow<MoonTilesPack> = callbackFlow {
        val listener = AssetPackStateUpdateListener { state ->
            if (state.name() == PACK_NAME) launch { send(fromState(state)) }
        }
        manager.registerListener(listener)
        launch { changes.collect { send(it ?: currentPack()) } }
        send(currentPack())
        awaitClose { manager.unregisterListener(listener) }
    }.distinctUntilChanged().flowOn(ioDispatcher)

    override suspend fun download() {
        try {
            manager.fetch(listOf(PACK_NAME)).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: AssetPackException) {
            changes.emit(packFromError(e.errorCode))
        } catch (@Suppress("TooGenericExceptionCaught") ignored: RuntimeException) {
            changes.emit(packFromError(AssetPackErrorCode.INTERNAL_ERROR))
        }
    }

    override suspend fun remove() {
        try {
            manager.removePack(PACK_NAME).await()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") ignored: RuntimeException) {
            // Nothing to remove, or no asset delivery: the state read below says which.
        }
        changes.emit(null)
    }

    override suspend fun measure(): List<StorageEntry> = withContext(ioDispatcher) {
        val installed = installedPack()
        listOf(StorageEntry(StorageKind.MOON_TILES, installed?.sizeBytes ?: 0L))
    }

    private suspend fun currentPack(): MoonTilesPack = withContext(ioDispatcher) {
        installedPack() ?: try {
            val states = manager.getPackStates(listOf(PACK_NAME)).await()
            states.packStates()[PACK_NAME]?.let { fromState(it) } ?: MoonTilesPack.Unavailable
        } catch (e: CancellationException) {
            throw e
        } catch (e: AssetPackException) {
            packFromError(e.errorCode)
        } catch (@Suppress("TooGenericExceptionCaught") ignored: RuntimeException) {
            MoonTilesPack.Unavailable
        }
    }

    private suspend fun fromState(state: AssetPackState): MoonTilesPack =
        installedPack() ?: packFromStatus(
            status = state.status(),
            errorCode = state.errorCode(),
            downloadedBytes = state.bytesDownloaded(),
            totalBytes = state.totalBytesToDownload(),
        )

    /** The pack's tiles folder when it is on the device, measured. */
    private suspend fun installedPack(): MoonTilesPack.Installed? = withContext(ioDispatcher) {
        val assets = runCatching { manager.getPackLocation(PACK_NAME)?.assetsPath() }.getOrNull()
        val directory = assets?.let { File(it, TILES_FOLDER) }?.takeIf(File::isDirectory)
        directory?.let { folder ->
            MoonTilesPack.Installed(folder.absolutePath, folder.walkBottomUp().filter(File::isFile).sumOf(File::length))
        }
    }

    companion object {
        /** `packName` in `moontiles/build.gradle.kts`. */
        const val PACK_NAME = "moon_tiles_deep"

        /** The folder the build script writes inside the pack's assets. */
        const val TILES_FOLDER = "moon_tiles"
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}
