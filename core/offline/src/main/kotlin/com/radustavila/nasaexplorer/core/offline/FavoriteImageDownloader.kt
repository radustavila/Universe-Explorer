package com.radustavila.nasaexplorer.core.offline

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.database.dao.FavoriteDao
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import com.radustavila.nasaexplorer.core.network.http.forImages
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody

/** What became of one attempt to keep a favourite's image offline. */
enum class FavoriteImageDownload {
    /** The copy was downloaded and recorded. */
    SAVED,

    /** A copy was already in place. */
    ALREADY_SAVED,

    /**
     * Nothing to keep: the favourite is gone or has no image, or its "image" is something else
     * (a video stream whose frame the image loader decodes on the fly) or too large to keep.
     */
    NOT_NEEDED,

    /** A transient failure - offline, timeout, rate limit, server error. Worth another attempt. */
    RETRY_LATER,

    /** The server refused for good (404 and other client errors). */
    FAILED,
}

/**
 * Downloads the snapshot image of one favourite into [FavoriteImageFiles] and records it on the
 * favourite's row.
 *
 * Goes through the shared OkHttp client (as derived for images, so per-host rate limits apply and
 * slow full-size downloads are not cut off) and streams to a partial file that is moved into place
 * only once complete, so a copy on disk is never truncated. The row is updated only if it still
 * shows the same image; a copy that lost that race is deleted again.
 */
@Singleton
class FavoriteImageDownloader @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val files: FavoriteImageFiles,
    okHttpClient: dagger.Lazy<OkHttpClient>,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    private val client by lazy { okHttpClient.get().forImages() }

    suspend fun download(source: FavoriteSource, itemId: String): FavoriteImageDownload = withContext(ioDispatcher) {
        val favorite = favoriteDao.find(source.key, itemId) ?: return@withContext FavoriteImageDownload.NOT_NEEDED
        val imageUrl = favorite.imageUrl ?: return@withContext FavoriteImageDownload.NOT_NEEDED
        val storedPath = favorite.localImagePath
        if (storedPath != null && files.resolve(storedPath).isFile) {
            return@withContext FavoriteImageDownload.ALREADY_SAVED
        }
        val url = imageUrl.toHttpUrlOrNull() ?: return@withContext FavoriteImageDownload.NOT_NEEDED

        try {
            client.newCall(Request.Builder().url(url).build()).await().use { response ->
                when {
                    response.isSuccessful -> keep(source, itemId, imageUrl, response)
                    response.code.isTransientFailure() -> FavoriteImageDownload.RETRY_LATER
                    else -> FavoriteImageDownload.FAILED
                }
            }
        } catch (_: IOException) {
            // Offline, timed out, or refused locally by the rate limiter.
            FavoriteImageDownload.RETRY_LATER
        }
    }

    private suspend fun keep(
        source: FavoriteSource,
        itemId: String,
        imageUrl: String,
        response: Response,
    ): FavoriteImageDownload {
        val body = response.body
        val extension = imageExtensionOf(body.contentType(), imageUrl)
        if (extension == null || body.contentLength() > MAX_IMAGE_BYTES) return FavoriteImageDownload.NOT_NEEDED

        files.deleteAll(source, itemId)
        val partial = files.partialFileFor(source, itemId)
        val bytes = try {
            body.copyTo(partial)
        } catch (failure: IOException) {
            partial.delete()
            throw failure
        }
        if (bytes == null || bytes == 0L) {
            partial.delete()
            return FavoriteImageDownload.NOT_NEEDED
        }

        val relativePath = files.relativePathFor(source, itemId, extension)
        val target = files.resolve(relativePath)
        if (!partial.renameTo(target)) {
            partial.delete()
            throw IOException("Could not move the download into place: $target")
        }

        val recorded = favoriteDao.setLocalImage(source.key, itemId, imageUrl, relativePath, bytes)
        return if (recorded == 1) {
            FavoriteImageDownload.SAVED
        } else {
            // Unfavourited, or given another image, while the download ran.
            target.delete()
            FavoriteImageDownload.NOT_NEEDED
        }
    }

    /** Streams the body into [target] and returns its size; `null` once it grows past [MAX_IMAGE_BYTES]. */
    private suspend fun ResponseBody.copyTo(target: File): Long? {
        target.parentFile?.mkdirs()
        var total = 0L
        byteStream().use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_IMAGE_BYTES) return null
                    output.write(buffer, 0, read)
                }
            }
        }
        return total
    }

    companion object {
        /** Far above any picture the app shows; anything bigger is not worth the space. */
        const val MAX_IMAGE_BYTES: Long = 40L * 1024 * 1024
        private const val BUFFER_BYTES = 64 * 1024
        private const val HTTP_TOO_MANY_REQUESTS = 429

        private val EXTENSIONS_BY_SUBTYPE = mapOf(
            "jpeg" to "jpg",
            "jpg" to "jpg",
            "pjpeg" to "jpg",
            "png" to "png",
            "gif" to "gif",
            "webp" to "webp",
            "avif" to "avif",
            "heic" to "heic",
            "heif" to "heif",
            "bmp" to "bmp",
        )

        /**
         * The file extension for a body that is a picture the image loader can decode, or `null`
         * for anything else. Some hosts label pictures `application/octet-stream`; for those the
         * URL's own extension decides.
         */
        internal fun imageExtensionOf(contentType: MediaType?, url: String): String? {
            if (contentType != null && contentType.type == "image") {
                return EXTENSIONS_BY_SUBTYPE[contentType.subtype.lowercase()]
            }
            val isUntyped = contentType == null ||
                (contentType.type == "application" && contentType.subtype == "octet-stream")
            if (!isUntyped) return null
            val urlExtension = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
                .substringAfterLast('.', missingDelimiterValue = "")
                .lowercase()
            return EXTENSIONS_BY_SUBTYPE[urlExtension]
        }

        private fun Int.isTransientFailure(): Boolean =
            this == HttpURLConnection.HTTP_CLIENT_TIMEOUT || this == HTTP_TOO_MANY_REQUESTS || this >= HTTP_SERVER_ERROR

        private const val HTTP_SERVER_ERROR = 500
    }
}

/** Runs the call on OkHttp's dispatcher, which also caps concurrent downloads per host. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, unused, _ -> unused.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        },
    )
}
