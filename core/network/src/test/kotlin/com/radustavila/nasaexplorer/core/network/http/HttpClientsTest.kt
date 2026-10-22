package com.radustavila.nasaexplorer.core.network.http

import com.radustavila.nasaexplorer.core.network.ratelimit.HostRateLimiter
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HttpClientsTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val server = MockWebServer()

    private val apiClient by lazy {
        apiHttpClient(
            configuration = NetworkConfiguration(isLoggingEnabled = false, callTimeoutSeconds = 30),
            rateLimiter = HostRateLimiter(budgets = emptySet(), timeProvider = FakeTimeProvider()),
            cacheDirectory = temporaryFolder.newFolder("http"),
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `the API client keeps its call timeout and a small disk cache`() {
        assertEquals(30_000, apiClient.callTimeoutMillis)
        assertEquals(HTTP_CACHE_BYTES, apiClient.cache?.maxSize())
    }

    @Test
    fun `the image client has no call timeout and no HTTP cache, but shares the connections`() {
        val imageClient = apiClient.forImages()

        assertEquals(0, imageClient.callTimeoutMillis)
        assertNull(imageClient.cache)
        assertSame(apiClient.connectionPool, imageClient.connectionPool)
        assertEquals(apiClient.readTimeoutMillis, imageClient.readTimeoutMillis)
        assertEquals(apiClient.interceptors, imageClient.interceptors)
    }

    @Test
    fun `a cacheable API response is served from the disk cache the second time`() {
        repeat(2) {
            server.enqueue(MockResponse().setBody("{}").addHeader("Cache-Control", "max-age=60"))
        }
        val request = Request.Builder().url(server.url("/kp.json")).build()

        apiClient.newCall(request).execute().use { it.body?.string() }
        val second = apiClient.newCall(request).execute().use { response ->
            response.body?.string()
            response
        }

        assertNotNull(second.cacheResponse)
        assertEquals(1, server.requestCount)
    }
}
