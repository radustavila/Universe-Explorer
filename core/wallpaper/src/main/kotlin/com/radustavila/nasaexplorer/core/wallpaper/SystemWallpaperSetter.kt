package com.radustavila.nasaexplorer.core.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.designsystem.util.loadBitmap
import com.radustavila.nasaexplorer.core.domain.wallpaper.WallpaperSetter
import com.radustavila.nasaexplorer.core.model.Apod
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Sets an entry's picture as the home screen wallpaper (the lock screen is left alone).
 *
 * The HD file is loaded through the application's image loader, bounded to the size the launcher
 * asks for, so a 6000-pixel original does not have to be decoded in full. Failures - no picture, a
 * device or profile that does not allow wallpapers, a decode error - are reported as "not set"
 * rather than thrown: the caller runs inside the sync worker and must not fail the sync over this.
 */
@Singleton
class SystemWallpaperSetter @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : WallpaperSetter {

    override suspend fun setWallpaper(apod: Apod): Boolean {
        val manager = WallpaperManager.getInstance(context)
        if (!manager.isWallpaperSupported || !manager.isSetWallpaperAllowed) return false

        val url = apod.fullImageUrl ?: return false
        val bitmap = context.loadBitmap(url, maxSizePx = targetSizePx(manager)) ?: return false
        return withContext(ioDispatcher) { apply(manager, bitmap) }
    }

    private fun apply(manager: WallpaperManager, bitmap: Bitmap): Boolean = try {
        manager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
        true
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        // A managed profile can forbid wallpaper changes after the check above.
        false
    }

    /** The launcher's preferred size, which covers its scrolling width; a sane bound otherwise. */
    private fun targetSizePx(manager: WallpaperManager): Int {
        val metrics = context.resources.displayMetrics
        val screen = maxOf(metrics.widthPixels, metrics.heightPixels)
        return maxOf(manager.desiredMinimumWidth, manager.desiredMinimumHeight, screen)
            .takeIf { it > 0 }
            ?.coerceAtMost(MAX_SIZE_PX)
            ?: MAX_SIZE_PX
    }

    private companion object {
        /** Sharp on any phone or tablet, and still a sane amount of memory to decode. */
        const val MAX_SIZE_PX = 2560
    }
}
