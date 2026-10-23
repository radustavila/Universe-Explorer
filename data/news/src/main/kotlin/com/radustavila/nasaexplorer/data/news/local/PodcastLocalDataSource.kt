package com.radustavila.nasaexplorer.data.news.local

import android.database.sqlite.SQLiteException
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.PodcastEpisode
import com.radustavila.nasaexplorer.core.model.PodcastShow
import com.radustavila.nasaexplorer.data.news.mapper.toEntity
import com.radustavila.nasaexplorer.data.news.mapper.toPodcastEpisodeOrNull
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Local boundary of the podcasts: cached episodes, progress and downloads in `news.db`. */
interface PodcastLocalDataSource {
    /** Every episode, newest first. */
    fun observeEpisodes(): Flow<List<PodcastEpisode>>
    fun observeEpisode(id: String): Flow<PodcastEpisode?>
    suspend fun replaceShow(
        show: PodcastShow,
        episodes: List<PodcastEpisode>,
        keepIds: Collection<String>,
        at: Instant,
    ): DataResult<Unit>

    /** Runs [block] on the database, with its failures as [AppError.Database]. */
    suspend fun <T> transaction(block: suspend PodcastDao.() -> T): DataResult<T>
}

class RoomPodcastLocalDataSource @Inject constructor(
    private val dao: PodcastDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : PodcastLocalDataSource {

    override fun observeEpisodes(): Flow<List<PodcastEpisode>> =
        dao.observeAll().map { rows -> rows.mapNotNull(PodcastEpisodeWithState::toPodcastEpisodeOrNull) }
            .flowOn(ioDispatcher)

    override fun observeEpisode(id: String): Flow<PodcastEpisode?> =
        dao.observeById(id).map { it?.toPodcastEpisodeOrNull() }.flowOn(ioDispatcher)

    override suspend fun replaceShow(
        show: PodcastShow,
        episodes: List<PodcastEpisode>,
        keepIds: Collection<String>,
        at: Instant,
    ): DataResult<Unit> = transaction { replaceShow(show.key, episodes.map { it.toEntity(at) }, keepIds) }

    override suspend fun <T> transaction(block: suspend PodcastDao.() -> T): DataResult<T> = withContext(ioDispatcher) {
        try {
            DataResult.Success(dao.block())
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
