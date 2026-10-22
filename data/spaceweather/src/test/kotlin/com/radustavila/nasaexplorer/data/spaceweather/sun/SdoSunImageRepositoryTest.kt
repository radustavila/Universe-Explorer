package com.radustavila.nasaexplorer.data.spaceweather.sun

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.SunChannel
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import com.radustavila.nasaexplorer.data.spaceweather.testJson
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.create

@RunWith(RobolectricTestRunner::class)
class SdoSunImageRepositoryTest {

    @get:Rule
    val server = FixtureServer()

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = TestScope(UnconfinedTestDispatcher())
    private val clock = FakeTimeProvider(currentInstant = Instant.parse("2026-10-22T16:30:00Z"))
    private val files by lazy {
        SunImageFiles(File(folder.root, "sun_images"), UnconfinedTestDispatcher(scope.testScheduler))
    }
    private val repository by lazy {
        SdoSunImageRepository(
            service = RetrofitFactory(testJson, OkHttpClient()).create(server.baseUrl).create(),
            files = files,
            imagery = SdoImagery(sdoBaseUrl = "https://sdo.test/", helioviewerBaseUrl = "https://helioviewer.test/"),
            dataStore = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
                File(folder.root, "sun_preferences.preferences_pb")
            },
            timeProvider = clock,
        )
    }

    private fun picture(bytes: String, lastModified: String = "Thu, 22 Oct 2026 16:02:08 GMT") = MockResponse()
        .setHeader("Last-Modified", lastModified)
        .setHeader("Content-Type", "image/jpeg")
        .setBody(Buffer().writeUtf8(bytes))

    @Test
    fun `the newest picture is downloaded at card size and dated by Last-Modified`() = scope.runTest {
        server.enqueue(picture("jpeg"))

        assertEquals(DataResult.Success(Unit), repository.refreshLatestImage(SunChannel.CORONA))

        val request = server.takeRequest()
        assertEquals("/assets/img/latest/latest_1024_0171.jpg", request.path)
        assertNull(request.getHeader("If-Modified-Since"))
        val image = repository.observeLatestImage(SunChannel.CORONA).first()!!
        assertEquals(Instant.parse("2026-10-22T16:02:08Z"), image.updatedAt)
        assertTrue(image.localUrl.startsWith("file:///"))
        assertEquals("jpeg", File(image.localUrl.removePrefix("file://")).readText())
        assertEquals("https://sdo.test/assets/img/latest/latest_2048_0171.jpg?v=1792684928", image.fullSizeUrl)
    }

    @Test
    fun `an unchanged picture is confirmed with If-Modified-Since and kept`() = scope.runTest {
        server.enqueue(picture("jpeg", lastModified = "Thu, 01 Oct 2026 09:05:00 GMT"))
        repository.refreshLatestImage(SunChannel.FLARES)
        server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(304))

        assertEquals(DataResult.Success(Unit), repository.refreshLatestImage(SunChannel.FLARES))

        assertEquals("Thu, 01 Oct 2026 09:05:00 GMT", server.takeRequest().getHeader("If-Modified-Since"))
        val image = repository.observeLatestImage(SunChannel.FLARES).first()!!
        assertEquals(Instant.parse("2026-10-01T09:05:00Z"), image.updatedAt)
    }

    @Test
    fun `a failure keeps the cached picture`() = scope.runTest {
        server.enqueue(picture("jpeg"))
        repository.refreshLatestImage(SunChannel.CORONA)
        server.enqueue(MockResponse().setResponseCode(503))

        val result = repository.refreshLatestImage(SunChannel.CORONA)

        assertEquals(503, ((result as DataResult.Failure).error as AppError.Http).code)
        val image = repository.observeLatestImage(SunChannel.CORONA).first()!!
        assertEquals("jpeg", File(image.localUrl.removePrefix("file://")).readText())
    }

    @Test
    fun `an empty answer is not a picture`() = scope.runTest {
        server.enqueue(MockResponse().setHeader("Last-Modified", "Thu, 22 Oct 2026 16:02:08 GMT"))

        assertEquals(DataResult.Failure(AppError.EmptyResponse), repository.refreshLatestImage(SunChannel.CORONA))
        assertNull(repository.observeLatestImage(SunChannel.CORONA).first())
    }

    @Test
    fun `the chosen channel is remembered, visible light until then`() = scope.runTest {
        assertEquals(SunChannel.VISIBLE_LIGHT, repository.selectedChannel.first())

        repository.selectChannel(SunChannel.MAGNETIC_FIELD)

        assertEquals(SunChannel.MAGNETIC_FIELD, repository.selectedChannel.first())
    }

    @Test
    fun `videos are the 512 pixel ones`() {
        assertEquals(
            "https://sdo.test/assets/img/latest/mpeg/latest_512_0304.mp4",
            repository.videoUrl(SunChannel.CHROMOSPHERE),
        )
    }
}
