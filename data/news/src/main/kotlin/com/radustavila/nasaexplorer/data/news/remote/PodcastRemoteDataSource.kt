package com.radustavila.nasaexplorer.data.news.remote

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.flatMap
import com.radustavila.nasaexplorer.core.model.PodcastShow
import com.radustavila.nasaexplorer.core.network.http.safeNetworkCall
import com.radustavila.nasaexplorer.data.news.remote.dto.FeedDocumentDto
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.xmlpull.v1.XmlPullParserException

/** Where each show's feed is read from; the app's are `BuildConfig` fields, tests point at a local server. */
class PodcastFeedUrls(private val urls: Map<PodcastShow, String>) {
    init {
        require(urls.keys == PodcastShow.entries.toSet()) { "Every podcast needs a URL" }
    }

    operator fun get(show: PodcastShow): String = urls.getValue(show)
}

/** Remote boundary of the podcasts: every call returns a [DataResult], never throws. */
interface PodcastRemoteDataSource {
    /** The show's feed as written (nasa.gov redirects it to Megaphone), or why it could not be read. */
    suspend fun fetch(show: PodcastShow): DataResult<FeedDocumentDto>
}

class RetrofitPodcastRemoteDataSource @Inject constructor(
    private val service: NewsFeedService,
    private val urls: PodcastFeedUrls,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : PodcastRemoteDataSource {

    override suspend fun fetch(show: PodcastShow): DataResult<FeedDocumentDto> = withContext(ioDispatcher) {
        safeNetworkCall { service.feed(urls[show]) }.flatMap(::parse)
    }

    private fun parse(body: ResponseBody): DataResult<FeedDocumentDto> = body.use {
        try {
            DataResult.Success(FeedParser.parseDocument(it.byteStream(), it.contentType()?.charset()?.name()))
        } catch (e: XmlPullParserException) {
            DataResult.Failure(AppError.Serialization(e.message))
        } catch (_: IOException) {
            DataResult.Failure(AppError.NoConnection)
        }
    }
}
