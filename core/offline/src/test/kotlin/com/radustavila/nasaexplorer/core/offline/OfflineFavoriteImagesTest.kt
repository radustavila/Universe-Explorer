package com.radustavila.nasaexplorer.core.offline

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.radustavila.nasaexplorer.core.database.NasaDatabase
import com.radustavila.nasaexplorer.core.database.dao.FavoriteDao
import com.radustavila.nasaexplorer.core.database.entity.FavoriteEntity
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The WorkManager side: which work is queued, and what each download outcome does to it. */
@RunWith(RobolectricTestRunner::class)
class OfflineFavoriteImagesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private lateinit var database: NasaDatabase
    private lateinit var favoriteDao: FavoriteDao
    private lateinit var files: FavoriteImageFiles
    private lateinit var downloader: FavoriteImageDownloader
    private lateinit var workManager: WorkManager
    private lateinit var offlineImages: WorkManagerOfflineFavoriteImages

    @Before
    fun setUp() {
        server.start()
        database = Room.inMemoryDatabaseBuilder(context, NasaDatabase::class.java).allowMainThreadQueries().build()
        favoriteDao = database.favoriteDao()
        files = FavoriteImageFiles(temporaryFolder.newFolder("favorites"))
        downloader = FavoriteImageDownloader(
            favoriteDao = favoriteDao,
            files = files,
            okHttpClient = { OkHttpClient.Builder().retryOnConnectionFailure(false).build() },
            ioDispatcher = UnconfinedTestDispatcher(),
        )
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
        offlineImages = WorkManagerOfflineFavoriteImages(
            workManager = { workManager },
            favoriteDao = favoriteDao,
            files = files,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() {
        database.close()
        server.shutdown()
    }

    @Test
    fun `a download waits for a network connection, one unique work per favourite`() {
        offlineImages.requestDownload(FavoriteSource.APOD, "2026-09-10")
        offlineImages.requestDownload(FavoriteSource.APOD, "2026-09-10")

        val infos = workInfos(FavoriteSource.APOD, "2026-09-10")
        assertEquals(1, infos.count { !it.state.isFinished })
        val pending = infos.single { !it.state.isFinished }
        assertEquals(NetworkType.CONNECTED, pending.constraints.requiredNetworkType)
        assertTrue(FavoriteImageDownloadWorker.TAG in pending.tags)
    }

    @Test
    fun `discarding cancels the download and deletes every copy`() = runTest {
        offlineImages.requestDownload(FavoriteSource.MARS, "1234")
        files.resolve("mars").mkdirs()
        files.resolve("mars/1234.jpg").writeBytes(byteArrayOf(1, 2, 3))
        files.resolve("mars/1234.part").writeBytes(byteArrayOf(1))
        files.resolve("mars/12345.jpg").writeBytes(byteArrayOf(1))

        offlineImages.discard(FavoriteSource.MARS, "1234")

        assertEquals(WorkInfo.State.CANCELLED, workInfos(FavoriteSource.MARS, "1234").single().state)
        // Another item whose id merely starts the same is untouched.
        assertEquals(listOf("12345.jpg"), files.root.resolve("mars").list()?.toList())
    }

    @Test
    fun `the backfill queues every favourite missing its copy, once`() = runTest {
        favoriteDao.save(entity(FavoriteSource.APOD, "2026-09-10", "https://example.org/a.jpg"))
        favoriteDao.save(entity(FavoriteSource.EPIC, "natural/2026-10-16/x", "https://example.org/b.jpg"))
        favoriteDao.save(entity(FavoriteSource.EXOPLANET, "Kepler-22 b", imageUrl = null))
        favoriteDao.save(entity(FavoriteSource.MARS, "done", "https://example.org/c.jpg"))
        favoriteDao.setLocalImage("mars", "done", "https://example.org/c.jpg", "mars/done.jpg", 3)
        files.resolve("mars/done.jpg").apply { parentFile?.mkdirs() }.writeBytes(ByteArray(3))

        offlineImages.backfillOnce()

        assertEquals(1, workInfos(FavoriteSource.APOD, "2026-09-10").size)
        assertEquals(1, workInfos(FavoriteSource.EPIC, "natural/2026-10-16/x").size)
        assertTrue(workInfos(FavoriteSource.EXOPLANET, "Kepler-22 b").isEmpty())
        assertTrue(workInfos(FavoriteSource.MARS, "done").isEmpty())

        // Favourites saved from now on queue their own download; a second backfill does nothing.
        favoriteDao.save(entity(FavoriteSource.NASA_LIBRARY, "later", "https://example.org/d.jpg"))
        offlineImages.backfillOnce()
        assertTrue(workInfos(FavoriteSource.NASA_LIBRARY, "later").isEmpty())
        assertTrue(files.backfillMarker.exists())
    }

    @Test
    fun `after a restore from backup, copies recorded in the database but missing on disk are queued again`() = runTest {
        favoriteDao.save(entity(FavoriteSource.MARS, "restored", "https://example.org/c.jpg"))
        favoriteDao.setLocalImage("mars", "restored", "https://example.org/c.jpg", "mars/restored.jpg", 3)

        offlineImages.backfillOnce()

        assertEquals(1, workInfos(FavoriteSource.MARS, "restored").size)
    }

    @Test
    fun `the worker keeps the image and succeeds`() = runTest {
        favoriteDao.save(entity(FavoriteSource.APOD, "2026-09-10", server.url("/a.jpg").toString()))
        server.enqueue(MockResponse().setHeader("Content-Type", "image/jpeg").setBody(Buffer().write(BYTES)))

        val result = worker(FavoriteSource.APOD, "2026-09-10").doWork()

        assertEquals(
            FavoriteImageDownload.SAVED.name,
            (result as ListenableWorker.Result.Success).outputData.getString(FavoriteImageDownloadWorker.KEY_OUTCOME),
        )
        assertTrue(files.resolve("apod/2026-09-10.jpg").isFile)
    }

    @Test
    fun `the worker retries transient failures until its attempts run out`() = runTest {
        favoriteDao.save(entity(FavoriteSource.APOD, "2026-09-10", server.url("/a.jpg").toString()))
        repeat(2) { server.enqueue(MockResponse().setResponseCode(503)) }

        assertTrue(worker(FavoriteSource.APOD, "2026-09-10").doWork() is ListenableWorker.Result.Retry)
        val lastAttempt = worker(FavoriteSource.APOD, "2026-09-10", FavoriteImageDownloadWorker.MAX_ATTEMPTS - 1)
        assertTrue(lastAttempt.doWork() is ListenableWorker.Result.Failure)
    }

    @Test
    fun `the worker gives up at once on a permanent failure and succeeds when there is nothing to keep`() = runTest {
        favoriteDao.save(entity(FavoriteSource.APOD, "2026-09-10", server.url("/a.jpg").toString()))
        server.enqueue(MockResponse().setResponseCode(404))

        assertTrue(worker(FavoriteSource.APOD, "2026-09-10").doWork() is ListenableWorker.Result.Failure)
        assertTrue(worker(FavoriteSource.APOD, "1999-01-01").doWork() is ListenableWorker.Result.Success)
        assertFalse(worker(source = null, itemId = "x").doWork() is ListenableWorker.Result.Success)
    }

    private fun workInfos(source: FavoriteSource, itemId: String): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(FavoriteImageDownloadWorker.uniqueWorkName(source, itemId)).get()

    private fun worker(source: FavoriteSource?, itemId: String, runAttemptCount: Int = 0): FavoriteImageDownloadWorker =
        TestListenableWorkerBuilder<FavoriteImageDownloadWorker>(context)
            .setInputData(
                workDataOf(
                    FavoriteImageDownloadWorker.KEY_SOURCE to source?.key,
                    FavoriteImageDownloadWorker.KEY_ITEM_ID to itemId,
                ),
            )
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ) = FavoriteImageDownloadWorker(appContext, workerParameters, downloader)
                },
            )
            .build()

    private fun entity(source: FavoriteSource, itemId: String, imageUrl: String?) = FavoriteEntity(
        source = source.key,
        itemId = itemId,
        title = "Title",
        imageUrl = imageUrl,
        savedAtEpochMillis = 1,
    )

    private companion object {
        val BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2)
    }
}
