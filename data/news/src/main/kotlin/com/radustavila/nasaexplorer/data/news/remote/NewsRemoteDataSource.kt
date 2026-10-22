package com.radustavila.nasaexplorer.data.news.remote

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.result.flatMap
import com.radustavila.nasaexplorer.core.model.NewsFeed
import com.radustavila.nasaexplorer.core.network.http.safeNetworkCall
import com.radustavila.nasaexplorer.data.news.remote.dto.FeedEntryDto
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.xmlpull.v1.XmlPullParserException

/** Where each feed is downloaded from; the app's are `BuildConfig` fields, tests point at a local server. */
class NewsFeedUrls(private val urls: Map<NewsFeed, String>) {
    init {
        require(urls.keys == NewsFeed.entries.toSet()) { "Every news feed needs a URL" }
    }

    operator fun get(feed: NewsFeed): String = urls.getValue(feed)
}

/** Remote boundary of the news module: every call returns a [DataResult], never throws. */
interface NewsRemoteDataSource {
    /** The feed's entries as written, or why they could not be read. */
    suspend fun fetch(feed: NewsFeed): DataResult<List<FeedEntryDto>>
}

class RetrofitNewsRemoteDataSource @Inject constructor(
    private val service: NewsFeedService,
    private val urls: NewsFeedUrls,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : NewsRemoteDataSource {

    override suspend fun fetch(feed: NewsFeed): DataResult<List<FeedEntryDto>> = withContext(ioDispatcher) {
        safeNetworkCall { service.feed(urls[feed]) }.flatMap(::parse)
    }

    /** A document that is not well-formed is a format problem, like JSON that does not decode. */
    private fun parse(body: ResponseBody): DataResult<List<FeedEntryDto>> = body.use {
        try {
            DataResult.Success(FeedParser.parse(it.byteStream(), it.contentType()?.charset()?.name()))
        } catch (e: XmlPullParserException) {
            DataResult.Failure(AppError.Serialization(e.message))
        } catch (_: IOException) {
            DataResult.Failure(AppError.NoConnection)
        }
    }
}
