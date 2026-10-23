package com.radustavila.nasaexplorer.data.news.download

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.model.PodcastDownloadState
import com.radustavila.nasaexplorer.core.network.http.forImages
import com.radustavila.nasaexplorer.data.news.local.PodcastDao
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody

/** What became of one attempt to download an episode. */
enum class PodcastDownloadOutcome {
    SAVED,

    /** Nothing to do: the download was cancelled, or the episode is gone. */
    NOT_NEEDED,

    /** Offline, timed out, rate-limited or a server error. Worth another attempt. */
    RETRY_LATER,

    /** The server refused for good, or the file is not audio. */
    FAILED,
}

/**
 * Downloads one episode's audio from its official enclosure URL into [PodcastFiles], following the
 * host's redirects (`chrt.fm` → Megaphone → its CDN). Streams to a partial file that is moved into
 * place only once complete, and records it only while the download is still wanted.
 */
@Singleton
class PodcastDownloader @Inject constructor(
    private val dao: PodcastDao,
    private val files: PodcastFiles,
    private val timeProvider: TimeProvider,
    okHttpClient: dagger.Lazy<OkHttpClient>,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    // The image variant: no overall call timeout, no HTTP cache; an episode is tens of megabytes.
    private val client by lazy { okHttpClient.get().forImages() }

    suspend fun download(episodeId: String): PodcastDownloadOutcome = withContext(ioDispatcher) {
        val wanted = dao.findDownload(episodeId) ?: return@withContext PodcastDownloadOutcome.NOT_NEEDED
        if (wanted.state == PodcastDownloadState.DOWNLOADED.name) return@withContext PodcastDownloadOutcome.SAVED
        val episode = dao.findEpisode(episodeId) ?: return@withContext PodcastDownloadOutcome.NOT_NEEDED
        val url = episode.audioUrl.toHttpUrlOrNull() ?: return@withContext fail(episodeId)

        mark(episodeId, PodcastDownloadState.DOWNLOADING)
        val outcome = try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                when {
                    response.isSuccessful -> keep(episodeId, response.body)
                    response.code.isTransientFailure() -> PodcastDownloadOutcome.RETRY_LATER
                    else -> PodcastDownloadOutcome.FAILED
                }
            }
        } catch (_: IOException) {
            files.partialFileFor(episodeId).delete()
            PodcastDownloadOutcome.RETRY_LATER
        }
        when (outcome) {
            PodcastDownloadOutcome.RETRY_LATER -> mark(episodeId, PodcastDownloadState.QUEUED)
            PodcastDownloadOutcome.FAILED -> fail(episodeId)
            PodcastDownloadOutcome.SAVED, PodcastDownloadOutcome.NOT_NEEDED -> Unit
        }
        outcome
    }

    /** Gives up on a download the worker will not retry again. */
    suspend fun giveUp(episodeId: String) = withContext(ioDispatcher) { fail(episodeId) }

    private suspend fun keep(episodeId: String, body: ResponseBody): PodcastDownloadOutcome {
        val type = body.contentType()
        if (type != null && type.type != "audio" && type.subtype != "octet-stream") return PodcastDownloadOutcome.FAILED

        val partial = files.partialFileFor(episodeId)
        val bytes = try {
            body.copyTo(partial)
        } catch (failure: IOException) {
            partial.delete()
            throw failure
        }
        val relativePath = files.relativePathFor(episodeId)
        val target = files.resolve(relativePath)
        if (bytes == 0L || !partial.renameTo(target)) {
            partial.delete()
            return PodcastDownloadOutcome.FAILED
        }
        val recorded = dao.updateDownload(
            episodeId = episodeId,
            state = PodcastDownloadState.DOWNLOADED.name,
            filePath = relativePath,
            bytes = bytes,
            at = timeProvider.now().toEpochMilli(),
        )
        return if (recorded == 1) {
            PodcastDownloadOutcome.SAVED
        } else {
            // Cancelled while the download ran.
            target.delete()
            PodcastDownloadOutcome.NOT_NEEDED
        }
    }

    private suspend fun ResponseBody.copyTo(target: File): Long {
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
                    output.write(buffer, 0, read)
                }
            }
        }
        return total
    }

    private suspend fun mark(episodeId: String, state: PodcastDownloadState) {
        dao.updateDownload(episodeId, state.name, filePath = null, bytes = 0, at = timeProvider.now().toEpochMilli())
    }

    private suspend fun fail(episodeId: String): PodcastDownloadOutcome {
        files.delete(episodeId)
        mark(episodeId, PodcastDownloadState.FAILED)
        return PodcastDownloadOutcome.FAILED
    }

    private fun Int.isTransientFailure(): Boolean =
        this == HttpURLConnection.HTTP_CLIENT_TIMEOUT || this == HTTP_TOO_MANY_REQUESTS || this >= HTTP_SERVER_ERROR

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVER_ERROR = 500
    }
}
