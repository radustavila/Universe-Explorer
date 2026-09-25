package com.radustavila.nasaexplorer.di

import android.media.MediaMetadataRetriever
import android.os.Build
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.size.Size
import coil3.size.pxOrElse
import com.radustavila.nasaexplorer.core.model.Apod
import java.io.IOException

/**
 * Loads the first frame of a video file as if it were an image.
 *
 * NASA publishes a fair number of APOD videos with `thumbnail_url: ""`, which left the feed showing
 * a generic play icon instead of the entry. For the videos NASA hosts itself there is no separate
 * poster to fetch, so the frame is decoded from the stream: [MediaMetadataRetriever] reads over
 * HTTP with range requests, so this costs the container header and one keyframe, not the whole file.
 */
internal class VideoFrameFetcher(
    private val uri: Uri,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(uri.toString())
            val frame = retriever.firstFrame(options.size)
                ?: throw IOException("No decodable frame in $uri")
            return ImageFetchResult(
                image = frame.asImage(),
                // The frame is decoded at the requested size rather than the video's own.
                isSampled = true,
                dataSource = DataSource.NETWORK,
            )
        } catch (e: RuntimeException) {
            // MediaMetadataRetriever reports an unreachable or undecodable source this way.
            throw IOException("Cannot read a frame from $uri", e)
        } finally {
            retriever.release()
        }
    }

    private fun MediaMetadataRetriever.firstFrame(size: Size) = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 -> getScaledFrameAtTime(
            /* timeUs = */ 0,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            size.width.pxOrElse { FALLBACK_FRAME_WIDTH },
            size.height.pxOrElse { FALLBACK_FRAME_HEIGHT },
        )

        // API 26 has no scaled variant; the frame comes out at its native size and Coil samples it.
        else -> getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (Apod.hasDecodableFirstFrame(data.toString())) {
                VideoFrameFetcher(data, options)
            } else {
                // Not a video: the network fetcher handles it.
                null
            }
    }

    private companion object {
        const val FALLBACK_FRAME_WIDTH = 1280
        const val FALLBACK_FRAME_HEIGHT = 720
    }
}
