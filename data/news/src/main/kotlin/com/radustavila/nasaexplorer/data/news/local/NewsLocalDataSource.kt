package com.radustavila.nasaexplorer.data.news.local

import android.database.sqlite.SQLiteException
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.NewsFeed
import com.radustavila.nasaexplorer.core.model.NewsItem
import com.radustavila.nasaexplorer.data.news.mapper.mergeFeeds
import com.radustavila.nasaexplorer.data.news.mapper.toEntity
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Local boundary of the news module: the cached entries of every feed. */
interface NewsLocalDataSource {
    /** Every item once, newest first. */
    fun observeNews(): Flow<List<NewsItem>>
    fun observeItem(id: String): Flow<NewsItem?>
    suspend fun replaceFeed(
        feed: NewsFeed,
        items: List<NewsItem>,
        keepIds: Collection<String>,
        at: Instant,
    ): DataResult<Unit>
}

class RoomNewsLocalDataSource @Inject constructor(
    private val dao: NewsDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : NewsLocalDataSource {

    override fun observeNews(): Flow<List<NewsItem>> =
        dao.observeAll().map(::mergeFeeds).flowOn(ioDispatcher)

    override fun observeItem(id: String): Flow<NewsItem?> =
        dao.observeById(id).map { rows -> mergeFeeds(rows).firstOrNull() }.flowOn(ioDispatcher)

    override suspend fun replaceFeed(
        feed: NewsFeed,
        items: List<NewsItem>,
        keepIds: Collection<String>,
        at: Instant,
    ): DataResult<Unit> = withContext(ioDispatcher) {
        try {
            DataResult.Success(dao.replaceFeed(feed.key, items.map { it.toEntity(feed, at) }, keepIds))
        } catch (e: SQLiteException) {
            DataResult.Failure(AppError.Database(e.message))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            // Room's own failures, e.g. a closed database.
            DataResult.Failure(AppError.Database(e.message))
        }
    }
}
