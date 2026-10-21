package com.radustavila.nasaexplorer.core.offline

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.database.NasaDatabase
import com.radustavila.nasaexplorer.core.database.dao.FavoriteDao
import com.radustavila.nasaexplorer.core.database.entity.FavoriteEntity
import com.radustavila.nasaexplorer.core.model.FavoriteSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FavoriteImageDownloaderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var database: NasaDatabase
    private lateinit var favoriteDao: FavoriteDao
    private lateinit var files: FavoriteImageFiles
    private lateinit var downloader: FavoriteImageDownloader

    @Before
    fun setUp() {
        server.start()
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NasaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        favoriteDao = database.favoriteDao()
        files = FavoriteImageFiles(temporaryFolder.newFolder("favorites"))
        downloader = FavoriteImageDownloader(
            favoriteDao = favoriteDao,
            files = files,
            // No silent retry, so each enqueued response answers exactly one attempt.
            okHttpClient = { OkHttpClient.Builder().retryOnConnectionFailure(false).build() },
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() {
        database.close()
        server.shutdown()
    }

    @Test
    fun `an image is kept under its source and id and recorded on the favourite`() = runTest {
        save(FavoriteSource.APOD, "2026-09-10", imageUrl = url("/ngc1300.jpg"))
        server.enqueue(image(JPEG_BYTES, "image/jpeg"))

        assertEquals(FavoriteImageDownload.SAVED, downloader.download(FavoriteSource.APOD, "2026-09-10"))

        val row = requireNotNull(favoriteDao.find("apod", "2026-09-10"))
        assertEquals("apod/2026-09-10.jpg", row.localImagePath)
        assertEquals(JPEG_BYTES.size.toLong(), row.localImageBytes)
        assertArrayEquals(JPEG_BYTES, files.resolve("apod/2026-09-10.jpg").readBytes())
        // No partial download is left behind.
        assertEquals(listOf("2026-09-10.jpg"), files.root.resolve("apod").list()?.toList())
        assertEquals(JPEG_BYTES.size.toLong(), favoriteDao.observeLocalImageBytes().first())
    }

    @Test
    fun `ids that are not file names are encoded and the extension follows the content type`() = runTest {
        save(FavoriteSource.EPIC, "natural/2026-10-16/epic_1b", imageUrl = url("/archive/epic_1b"))
        server.enqueue(image(PNG_BYTES, "image/png"))

        downloader.download(FavoriteSource.EPIC, "natural/2026-10-16/epic_1b")

        assertEquals(
            "epic/natural%2F2026-10-16%2Fepic_1b.png",
            favoriteDao.find("epic", "natural/2026-10-16/epic_1b")?.localImagePath,
        )
    }

    @Test
    fun `a copy already in place is not downloaded again`() = runTest {
        save(FavoriteSource.MARS, "1234", imageUrl = url("/1234.jpg"))
        server.enqueue(image(JPEG_BYTES, "image/jpeg"))
        downloader.download(FavoriteSource.MARS, "1234")

        assertEquals(FavoriteImageDownload.ALREADY_SAVED, downloader.download(FavoriteSource.MARS, "1234"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a copy that went missing is downloaded again`() = runTest {
        save(FavoriteSource.MARS, "1234", imageUrl = url("/1234.jpg"))
        server.enqueue(image(JPEG_BYTES, "image/jpeg"))
        server.enqueue(image(JPEG_BYTES, "image/jpeg"))
        downloader.download(FavoriteSource.MARS, "1234")
        files.resolve("mars/1234.jpg").delete()

        assertEquals(FavoriteImageDownload.SAVED, downloader.download(FavoriteSource.MARS, "1234"))
        assertTrue(files.resolve("mars/1234.jpg").isFile)
    }

    @Test
    fun `a video preview is not kept, and nothing is recorded`() = runTest {
        save(FavoriteSource.APOD, "2026-09-08", imageUrl = url("/aurora.mp4"))
        server.enqueue(image(JPEG_BYTES, "video/mp4"))

        assertEquals(FavoriteImageDownload.NOT_NEEDED, downloader.download(FavoriteSource.APOD, "2026-09-08"))

        assertNull(favoriteDao.find("apod", "2026-09-08")?.localImagePath)
        assertEquals(0L, files.totalBytes())
    }

    @Test
    fun `a picture served without a content type is recognised by its URL`() = runTest {
        save(FavoriteSource.NASA_LIBRARY, "PIA001", imageUrl = url("/PIA001~large.JPG?x=1"))
        server.enqueue(image(JPEG_BYTES, "application/octet-stream"))

        assertEquals(FavoriteImageDownload.SAVED, downloader.download(FavoriteSource.NASA_LIBRARY, "PIA001"))
        assertEquals("nasa_library/PIA001.jpg", favoriteDao.find("nasa_library", "PIA001")?.localImagePath)
    }

    @Test
    fun `server errors are worth retrying, client errors are not`() = runTest {
        save(FavoriteSource.MARS, "1234", imageUrl = url("/1234.jpg"))
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(404))

        assertEquals(FavoriteImageDownload.RETRY_LATER, downloader.download(FavoriteSource.MARS, "1234"))
        assertEquals(FavoriteImageDownload.RETRY_LATER, downloader.download(FavoriteSource.MARS, "1234"))
        assertEquals(FavoriteImageDownload.FAILED, downloader.download(FavoriteSource.MARS, "1234"))
        assertEquals(0L, files.totalBytes())
    }

    @Test
    fun `an unreachable server is worth retrying`() = runTest {
        save(FavoriteSource.MARS, "1234", imageUrl = url("/1234.jpg"))
        server.shutdown()

        assertEquals(FavoriteImageDownload.RETRY_LATER, downloader.download(FavoriteSource.MARS, "1234"))
        assertNull(favoriteDao.find("mars", "1234")?.localImagePath)
    }

    @Test
    fun `an empty body is not kept`() = runTest {
        save(FavoriteSource.MARS, "1234", imageUrl = url("/1234.jpg"))
        server.enqueue(MockResponse().setHeader("Content-Type", "image/jpeg"))

        assertEquals(FavoriteImageDownload.NOT_NEEDED, downloader.download(FavoriteSource.MARS, "1234"))
        assertEquals(0L, files.totalBytes())
    }

    @Test
    fun `nothing is downloaded for a favourite that is gone or has no image`() = runTest {
        save(FavoriteSource.EXOPLANET, "Kepler-22 b", imageUrl = null)

        assertEquals(FavoriteImageDownload.NOT_NEEDED, downloader.download(FavoriteSource.EXOPLANET, "Kepler-22 b"))
        assertEquals(FavoriteImageDownload.NOT_NEEDED, downloader.download(FavoriteSource.APOD, "1999-01-01"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a copy finished after the item was unfavourited is deleted again`() = runTest {
        save(FavoriteSource.APOD, "2026-09-10", imageUrl = url("/ngc1300.jpg"))
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                // The user takes the heart back while the picture is on its way.
                runBlocking { favoriteDao.delete("apod", "2026-09-10") }
                return image(JPEG_BYTES, "image/jpeg")
            }
        }

        assertEquals(FavoriteImageDownload.NOT_NEEDED, downloader.download(FavoriteSource.APOD, "2026-09-10"))
        assertEquals(0L, files.totalBytes())
    }

    @Test
    fun `a new snapshot image replaces the old copy`() = runTest {
        save(FavoriteSource.APOD, "2026-09-10", imageUrl = url("/old.png"))
        server.enqueue(image(PNG_BYTES, "image/png"))
        downloader.download(FavoriteSource.APOD, "2026-09-10")

        favoriteDao.save(entity(FavoriteSource.APOD, "2026-09-10", url("/new.jpg")))
        server.enqueue(image(JPEG_BYTES, "image/jpeg"))

        assertEquals(FavoriteImageDownload.SAVED, downloader.download(FavoriteSource.APOD, "2026-09-10"))
        assertEquals(listOf("2026-09-10.jpg"), files.root.resolve("apod").list()?.toList())
        assertEquals("apod/2026-09-10.jpg", favoriteDao.find("apod", "2026-09-10")?.localImagePath)
    }

    @Test
    fun `only decodable pictures get an extension`() {
        val jpeg = "image/jpeg".toMediaType()
        assertEquals("jpg", FavoriteImageDownloader.imageExtensionOf(jpeg, "https://x.org/a"))
        assertEquals("webp", FavoriteImageDownloader.imageExtensionOf("image/webp".toMediaType(), "https://x.org/a"))
        assertNull(FavoriteImageDownloader.imageExtensionOf("image/svg+xml".toMediaType(), "https://x.org/a.svg"))
        assertNull(FavoriteImageDownloader.imageExtensionOf("text/html".toMediaType(), "https://x.org/a.jpg"))
        assertEquals("png", FavoriteImageDownloader.imageExtensionOf(null, "https://x.org/a.PNG#top"))
        assertNull(FavoriteImageDownloader.imageExtensionOf(null, "https://x.org/a.mp4"))
    }

    @Test
    fun `file names are unambiguous and stay within the file system's limits`() {
        assertEquals("2026-09-10", FavoriteImageFiles.fileNameOf("2026-09-10"))
        assertEquals("Kepler-22%20b", FavoriteImageFiles.fileNameOf("Kepler-22 b"))
        // The dot is encoded too, so the extension is always what follows the last one.
        assertEquals("HD%20189733%2Eb", FavoriteImageFiles.fileNameOf("HD 189733.b"))
        assertEquals("%C3%A9", FavoriteImageFiles.fileNameOf("é"))
        val long = FavoriteImageFiles.fileNameOf("x".repeat(500))
        assertEquals(64, long.length)
        assertFalse(long == FavoriteImageFiles.fileNameOf("x".repeat(501)))
    }

    private suspend fun save(source: FavoriteSource, itemId: String, imageUrl: String?) {
        favoriteDao.save(entity(source, itemId, imageUrl))
    }

    private fun entity(source: FavoriteSource, itemId: String, imageUrl: String?) = FavoriteEntity(
        source = source.key,
        itemId = itemId,
        title = "Title",
        imageUrl = imageUrl,
        savedAtEpochMillis = 1,
    )

    private fun url(path: String): String = server.url(path).toString()

    private fun image(bytes: ByteArray, contentType: String): MockResponse =
        MockResponse().setHeader("Content-Type", contentType).setBody(Buffer().write(bytes))

    private companion object {
        val JPEG_BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3, 4)
        val PNG_BYTES = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 5, 6, 7)
    }
}
