package com.radustavila.nasaexplorer.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import coil3.ImageLoader
import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.database.NasaDatabase
import com.radustavila.nasaexplorer.core.domain.storage.CacheCleaner
import com.radustavila.nasaexplorer.core.domain.storage.StorageEntry
import com.radustavila.nasaexplorer.core.domain.storage.StorageKind
import com.radustavila.nasaexplorer.core.domain.storage.StorageUsageSource
import com.radustavila.nasaexplorer.data.dsn.local.DsnDatabase
import com.radustavila.nasaexplorer.data.earthevents.local.EarthEventsDatabase
import com.radustavila.nasaexplorer.data.exoplanets.local.ExoplanetsDatabase
import com.radustavila.nasaexplorer.data.launches.local.LaunchesDatabase
import com.radustavila.nasaexplorer.data.mars.local.MarsDatabase
import com.radustavila.nasaexplorer.data.nasaimages.local.NasaImagesDatabase
import com.radustavila.nasaexplorer.data.sky.local.SkyDatabase
import com.radustavila.nasaexplorer.data.smallbodies.local.SmallBodiesDatabase
import com.radustavila.nasaexplorer.data.spaceweather.local.SpaceWeatherDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The image loader's caches: the disk cache is measured and, with the memory cache, emptied by
 * "Clear cache". Offline copies of favourite images live outside it (under `filesDir`), so clearing
 * it never takes a favourite's picture away for good.
 */
class ImageCacheStorage @Inject constructor(
    private val imageLoader: dagger.Lazy<ImageLoader>,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : StorageUsageSource, CacheCleaner {

    override suspend fun measure(): List<StorageEntry> = withContext(ioDispatcher) {
        listOf(StorageEntry(StorageKind.IMAGE_CACHE, imageLoader.get().diskCache?.size ?: 0L))
    }

    override suspend fun clear() {
        withContext(ioDispatcher) {
            val loader = imageLoader.get()
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
    }
}

/**
 * Sizes of the Room databases, as the composition root is the one place that knows them
 * all. The feeds' mirrors count as cached data; the main database holds the user's library
 * (pictures of the day, Earth photos and the favourites table). They are emptied through their
 * evictors, not here.
 */
class DatabaseStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : StorageUsageSource {

    override suspend fun measure(): List<StorageEntry> = withContext(ioDispatcher) {
        listOf(
            StorageEntry(StorageKind.CACHED_DATA, MIRROR_DATABASES.sumOf(::sizeOf)),
            StorageEntry(StorageKind.LIBRARY, sizeOf(NasaDatabase.NAME)),
        )
    }

    /**
     * What the database's data takes: its pages in use, read through a separate read-only connection
     * (which sees the write-ahead log). SQLite never shrinks its files when rows are deleted, so
     * file sizes would not drop after "Clear cache" and would even grow by the log. Falls back to
     * the files' sizes when the database cannot be opened.
     */
    private fun sizeOf(name: String): Long {
        val database = context.getDatabasePath(name)
        if (!database.exists()) return 0
        return try {
            SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                (db.pragma("page_count") - db.pragma("freelist_count")) * db.pragma("page_size")
            }
        } catch (_: SQLiteException) {
            (listOf(database) + SQLITE_SUFFIXES.map { File(database.path + it) }).sumOf(File::length)
        }
    }

    private fun SQLiteDatabase.pragma(name: String): Long =
        rawQuery("PRAGMA $name", null).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }

    companion object {
        /** Local copies of remote feeds: everything here can be downloaded again. */
        val MIRROR_DATABASES: List<String> = listOf(
            LaunchesDatabase.NAME,
            EarthEventsDatabase.NAME,
            ExoplanetsDatabase.NAME,
            MarsDatabase.NAME,
            NasaImagesDatabase.NAME,
            SmallBodiesDatabase.NAME,
            SpaceWeatherDatabase.NAME,
            SkyDatabase.NAME,
            DsnDatabase.NAME,
        )

        private val SQLITE_SUFFIXES = listOf("-wal", "-shm", "-journal")
    }
}
