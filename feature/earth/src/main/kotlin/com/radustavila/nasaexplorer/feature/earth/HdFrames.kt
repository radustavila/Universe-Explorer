package com.radustavila.nasaexplorer.feature.earth

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.radustavila.nasaexplorer.core.common.concurrency.mapConcurrently
import com.radustavila.nasaexplorer.core.designsystem.util.decodeImage
import com.radustavila.nasaexplorer.core.designsystem.util.preloadImage
import kotlinx.coroutines.CompletableDeferred

/** What the rotation asks of the image loader; tests swap in their own. */
internal interface FrameImageLoader {
    /** Brings [url] into the disk cache. Failing is not an error: the frame then shows its thumbnail. */
    suspend fun download(url: String)

    /** Decodes [url] into memory as a frame [sizePx] square draws it. */
    suspend fun decode(url: String, sizePx: Int)
}

@Composable
internal fun rememberFrameImageLoader(): FrameImageLoader {
    val context = LocalContext.current.applicationContext
    return remember(context) { CoilFrameImageLoader(context) }
}

private class CoilFrameImageLoader(private val context: Context) : FrameImageLoader {
    override suspend fun download(url: String) {
        context.preloadImage(url)
    }

    override suspend fun decode(url: String, sizePx: Int) {
        context.decodeImage(url, sizePx)
    }
}

/**
 * Readies a day's HD frames ([urls], in playing order) ahead of the rotation.
 *
 * [preload] downloads all of them into the disk cache when the day opens, the frames due soonest
 * first, so each is on the device well before its turn and the whole day replays offline.
 * [awaitFrame] is what the rotation holds a frame for before showing the next one.
 */
internal class HdFramePreloader(
    private val urls: List<String>,
    private val loader: FrameImageLoader,
) {
    private val downloaded = List(urls.size) { CompletableDeferred<Unit>() }

    /**
     * Downloads every frame, [MAX_CONCURRENT_DOWNLOADS] at a time, starting with the one after
     * [currentIndex] and wrapping around to it: the frame on screen is already being loaded by
     * the pager itself.
     */
    suspend fun preload(currentIndex: Int) {
        val order = List(urls.size) { offset -> (currentIndex + 1 + offset).mod(urls.size) }
        order.mapConcurrently(MAX_CONCURRENT_DOWNLOADS) { index ->
            try {
                loader.download(urls[index])
            } finally {
                downloaded[index].complete(Unit)
            }
        }
    }

    /**
     * Returns once frame [index] is downloaded (or failed to) and decoded into memory at [sizePx],
     * the pager's page size, so it draws sharp in the first frame it is on screen.
     */
    suspend fun awaitFrame(index: Int, sizePx: Int) {
        downloaded[index].await()
        loader.decode(urls[index], sizePx)
    }

    private companion object {
        /** A day is about a dozen 200 KB photos; EPIC's archive host publishes no quota. */
        const val MAX_CONCURRENT_DOWNLOADS = 3
    }
}
