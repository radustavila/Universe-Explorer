package com.radustavila.nasaexplorer.core.network.ratelimit

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.network.http.RetrofitFactory
import com.radustavila.nasaexplorer.core.network.http.safeNetworkCall
import com.radustavila.nasaexplorer.core.testing.fake.FakeTimeProvider
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import java.time.Duration
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.create
import retrofit2.http.GET

/**
 * The whole stack a data module uses - shared OkHttp client, Retrofit, `safeNetworkCall` - against
 * recorded responses, so "a 429 from any host surfaces as RateLimited and blocks until Retry-After"
 * is checked end to end rather than per piece.
 */
class RateLimitInterceptorTest {

    @get:Rule
    val server = FixtureServer()

    private val clock = FakeTimeProvider()
    private lateinit var limiter: HostRateLimiter
    private lateinit var service: SampleService

    @Before
    fun setUp() {
        setUpWith(budgets = emptySet())
    }

    private fun setUpWith(budgets: Set<HostRateBudget>) {
        limiter = HostRateLimiter(budgets = budgets, timeProvider = clock)
        val client = OkHttpClient.Builder().addInterceptor(RateLimitInterceptor(limiter)).build()
        service = RetrofitFactory(json = Json { ignoreUnknownKeys = true }, callFactory = client)
            .create(server.baseUrl)
            .create()
    }

    @Test
    fun `a recorded response passes through untouched`() = runTest {
        server.enqueueFixture("sample/status.json")

        val result = safeNetworkCall { service.status() }

        assertEquals(DataResult.Success(SampleStatus(status = "ok", count = 3)), result)
    }

    @Test
    fun `a 429 surfaces as RateLimited and further calls are refused locally until Retry-After`() =
        runTest {
            server.enqueueStatus(code = 429, headers = mapOf("Retry-After" to "90"))
            server.enqueueFixture("sample/status.json")

            val throttled = safeNetworkCall { service.status() }
            val refused = safeNetworkCall { service.status() }

            assertEquals(DataResult.Failure(AppError.RateLimited(retryAfterSeconds = 90)), throttled)
            assertEquals(DataResult.Failure(AppError.RateLimited(retryAfterSeconds = 90)), refused)
            // The refused call never reached the server.
            assertEquals(1, server.requestCount)

            clock.currentInstant = clock.currentInstant.plusSeconds(90)
            val afterCooldown = safeNetworkCall { service.status() }

            assertEquals(DataResult.Success(SampleStatus(status = "ok", count = 3)), afterCooldown)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `a spent budget refuses the call without a round trip`() = runTest {
        setUpWith(
            setOf(HostRateBudget(host = server.hostName, maxRequests = 1, window = Duration.ofHours(1))),
        )
        server.enqueueFixture("sample/status.json")

        safeNetworkCall { service.status() }
        val refused = safeNetworkCall { service.status() }

        assertEquals(DataResult.Failure(AppError.RateLimited(retryAfterSeconds = 3_600)), refused)
        assertEquals(1, server.requestCount)
    }

    interface SampleService {
        @GET("status")
        suspend fun status(): SampleStatus
    }

    @Serializable
    data class SampleStatus(val status: String, val count: Int)
}
