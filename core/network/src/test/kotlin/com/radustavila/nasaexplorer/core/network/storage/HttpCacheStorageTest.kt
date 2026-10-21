package com.radustavila.nasaexplorer.core.network.storage

import com.radustavila.nasaexplorer.core.domain.storage.StorageEntry
import com.radustavila.nasaexplorer.core.domain.storage.StorageKind
import com.radustavila.nasaexplorer.core.network.http.NetworkConfiguration
import com.radustavila.nasaexplorer.core.network.http.apiHttpClient
import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateLimiter
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HttpCacheStorageTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val server = MockWebServer()

    private val client by lazy {
        apiHttpClient(
            configuration = NetworkConfiguration(isLoggingEnabled = false),
            rateLimiter = HostRateLimiter(budgets = emptySet(), timeProvider = FakeTimeProvider()),
            cacheDirectory = temporaryFolder.newFolder("http"),
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `measures the cached responses and empties them`() = runTest {
        server.enqueue(MockResponse().setBody("x".repeat(4_096)).setHeader("Cache-Control", "max-age=3600"))
        client.newCall(Request.Builder().url(server.url("/feed")).build()).execute().use { it.body.string() }
        val storage = HttpCacheStorage({ client }, UnconfinedTestDispatcher(testScheduler))

        val before = storage.measure().single()
        assertEquals(StorageKind.HTTP_CACHE, before.kind)
        assertTrue("${before.bytes}", before.bytes >= 4_096)

        storage.clear()

        assertEquals(listOf(StorageEntry(StorageKind.HTTP_CACHE, 0)), storage.measure())
    }
}
