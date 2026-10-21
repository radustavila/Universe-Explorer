package com.radustavila.nasaexplorer.core.offline

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import coil3.ColorImage
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.size.Size
import com.radustavila.nasaexplorer.core.database.NasaDatabase
import com.radustavila.nasaexplorer.core.database.dao.FavoriteDao
import com.radustavila.nasaexplorer.core.database.entity.FavoriteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflineFavoriteImageInterceptorTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: NasaDatabase
    private lateinit var favoriteDao: FavoriteDao
    private lateinit var files: FavoriteImageFiles

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, NasaDatabase::class.java).allowMainThreadQueries().build()
        favoriteDao = database.favoriteDao()
        files = FavoriteImageFiles(temporaryFolder.newFolder("favorites"))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `the index maps a favourite's remote image to its offline copy, and follows the table`() = runTest {
        val index = index()
        favoriteDao.save(entity("2026-09-10", REMOTE_URL))

        assertNull(index.offlineUriFor(REMOTE_URL))

        favoriteDao.setLocalImage("apod", "2026-09-10", REMOTE_URL, "apod/2026-09-10.jpg", 3)
        index.awaitUri(REMOTE_URL, "file://${files.root.path}/apod/2026-09-10.jpg")

        favoriteDao.delete("apod", "2026-09-10")
        index.awaitUri(REMOTE_URL, expected = null)
    }

    /** Room reports table changes asynchronously, so the index catches up a moment later. */
    private suspend fun OfflineFavoriteImageIndex.awaitUri(imageUrl: String, expected: String?) {
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                while (offlineUriFor(imageUrl) != expected) delay(10)
            }
        }
        assertEquals(expected, offlineUriFor(imageUrl))
    }

    @Test
    fun `a request for a favourite's image loads the offline copy`() = runTest {
        favoriteDao.save(entity("2026-09-10", REMOTE_URL))
        favoriteDao.setLocalImage("apod", "2026-09-10", REMOTE_URL, "apod/2026-09-10.jpg", 3)
        val chain = RecordingChain(request(REMOTE_URL))

        OfflineFavoriteImageInterceptor(index()).intercept(chain)

        assertEquals(listOf("file://${files.root.path}/apod/2026-09-10.jpg"), chain.proceededWith)
    }

    @Test
    fun `any other request, or a copy that fails to load, goes on to the network`() = runTest {
        favoriteDao.save(entity("2026-09-10", REMOTE_URL))
        favoriteDao.setLocalImage("apod", "2026-09-10", REMOTE_URL, "apod/2026-09-10.jpg", 3)
        val interceptor = OfflineFavoriteImageInterceptor(index())

        val other = RecordingChain(request("https://example.org/other.jpg"))
        interceptor.intercept(other)
        assertEquals(listOf("https://example.org/other.jpg"), other.proceededWith)

        val broken = RecordingChain(request(REMOTE_URL), failingData = "file://${files.root.path}/apod/2026-09-10.jpg")
        val result = interceptor.intercept(broken)
        assertEquals(listOf("file://${files.root.path}/apod/2026-09-10.jpg", REMOTE_URL), broken.proceededWith)
        assertEquals(REMOTE_URL, result.request.data)
    }

    private fun kotlinx.coroutines.test.TestScope.index() = OfflineFavoriteImageIndex(
        favoriteDao = favoriteDao,
        files = files,
        applicationScope = backgroundScope,
        ioDispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    private fun request(data: String): ImageRequest = ImageRequest.Builder(context).data(data).build()

    private fun entity(itemId: String, imageUrl: String) = FavoriteEntity(
        source = "apod",
        itemId = itemId,
        title = "Title",
        imageUrl = imageUrl,
        savedAtEpochMillis = 1,
    )

    /** Records the data of every request that reached the rest of the pipeline. */
    private class RecordingChain(
        override val request: ImageRequest,
        private val failingData: String? = null,
        private val proceeded: MutableList<String> = mutableListOf(),
    ) : Interceptor.Chain {

        val proceededWith: List<String> get() = proceeded

        override val size: Size = Size.ORIGINAL

        override fun withRequest(request: ImageRequest): Interceptor.Chain =
            RecordingChain(request, failingData, proceeded)

        override fun withSize(size: Size): Interceptor.Chain = this

        override suspend fun proceed(): ImageResult {
            val data = request.data as String
            proceeded += data
            return if (data == failingData) {
                ErrorResult(image = null, request = request, throwable = IllegalStateException("gone"))
            } else {
                SuccessResult(image = ColorImage(), request = request)
            }
        }
    }

    private companion object {
        const val REMOTE_URL = "https://apod.nasa.gov/apod/image/ngc1300.jpg"
    }
}
