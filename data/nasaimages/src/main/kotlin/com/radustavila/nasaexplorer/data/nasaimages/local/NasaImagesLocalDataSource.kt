package com.radustavila.nasaexplorer.data.nasaimages.local

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.GalleryRail
import com.radustavila.nasaexplorer.core.model.NasaMedia
import com.radustavila.nasaexplorer.data.nasaimages.mapper.toDomain
import com.radustavila.nasaexplorer.data.nasaimages.mapper.toEntity
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Local boundary of the library module: the rails the gallery observes, and the saved items. */
interface NasaImagesLocalDataSource {
    fun observeRails(): Flow<Map<GalleryRail, List<NasaMedia>>>
    fun observeMedia(nasaId: String): Flow<NasaMedia?>
    suspend fun replaceRails(rails: Map<GalleryRail, List<NasaMedia>>, at: Instant): DataResult<Unit>
    suspend fun store(media: NasaMedia, at: Instant): DataResult<Unit>
}

class RoomNasaImagesLocalDataSource @Inject constructor(
    private val dao: NasaMediaDao,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : NasaImagesLocalDataSource {

    override fun observeRails(): Flow<Map<GalleryRail, List<NasaMedia>>> =
        dao.observeRailRows()
            .map { rows ->
                val byRail = rows.groupBy({ it.rail }, { it.media.toDomain() })
                // Declaration order, not the table's alphabetical one; unknown keys are dropped.
                GalleryRail.entries
                    .mapNotNull { rail -> byRail[rail.key]?.let { rail to it } }
                    .toMap()
            }
            .flowOn(ioDispatcher)

    override fun observeMedia(nasaId: String): Flow<NasaMedia?> =
        dao.observeMedia(nasaId).map { it?.toDomain() }.flowOn(ioDispatcher)

    override suspend fun replaceRails(rails: Map<GalleryRail, List<NasaMedia>>, at: Instant): DataResult<Unit> =
        write {
            val media = rails.values.flatten().distinctBy(NasaMedia::nasaId).map { it.toEntity(at) }
            val items = rails.entries.associate { (rail, list) ->
                rail.key to list.distinctBy(NasaMedia::nasaId).mapIndexed { index, item ->
                    RailItemEntity(rail = rail.key, nasaId = item.nasaId, position = index)
                }
            }
            dao.replaceRails(media, items)
        }

    override suspend fun store(media: NasaMedia, at: Instant): DataResult<Unit> =
        write { dao.upsertMedia(listOf(media.toEntity(at))) }

    private suspend fun write(block: suspend () -> Unit): DataResult<Unit> = withContext(ioDispatcher) {
        try {
            DataResult.Success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            DataResult.Failure(AppError.Database(throwable.message))
        }
    }
}
