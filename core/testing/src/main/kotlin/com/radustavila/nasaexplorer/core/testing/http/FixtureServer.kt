package com.radustavila.nasaexplorer.core.testing.http

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.rules.ExternalResource

/**
 * MockWebServer as a JUnit rule that answers with responses recorded from the real APIs.
 *
 * Every data module tests its DTOs and its repository against the same shape: record a real
 * response once, check it in under `src/test/resources/fixtures/`, then
 *
 * ```
 * @get:Rule val server = FixtureServer()
 *
 * server.enqueueFixture("launches/upcoming.json")
 * val service = retrofitFactory.create(server.baseUrl).create<LaunchLibraryService>()
 * ```
 *
 * Responses are served in the order they were enqueued; [takeRequest] returns what the client sent.
 */
class FixtureServer : ExternalResource() {

    private val server = MockWebServer()

    /** Base URL with a trailing slash, ready for `Retrofit.Builder.baseUrl`. */
    val baseUrl: String get() = server.url("/").toString()

    /** Host the client actually connects to, e.g. to register a rate budget for it in a test. */
    val hostName: String get() = server.hostName

    override fun before() {
        server.start()
    }

    override fun after() {
        server.shutdown()
    }

    /** Serves `fixtures/[path]` from the test classpath as JSON. */
    fun enqueueFixture(
        path: String,
        code: Int = HTTP_OK,
        headers: Map<String, String> = emptyMap(),
    ) {
        enqueueJson(body = Fixtures.read(path), code = code, headers = headers)
    }

    /** Serves an inline JSON [body], for edge cases not worth a file. */
    fun enqueueJson(
        body: String,
        code: Int = HTTP_OK,
        headers: Map<String, String> = emptyMap(),
    ) {
        enqueue(MockResponse().setResponseCode(code).setBody(body).withHeaders(headers))
    }

    /** Serves a bodyless status, e.g. a 429 with a `Retry-After` header. */
    fun enqueueStatus(code: Int, headers: Map<String, String> = emptyMap()) {
        enqueue(MockResponse().setResponseCode(code).withHeaders(headers))
    }

    /** Escape hatch for responses the helpers above cannot express (delays, socket policies). */
    fun enqueue(response: MockResponse) {
        server.enqueue(response)
    }

    /**
     * Answers every request with [responder] instead of in enqueue order, for a client that calls
     * several endpoints concurrently (their order of arrival is not fixed).
     */
    fun route(responder: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = responder(request)
        }
    }

    /** `fixtures/[path]` as a JSON response, for [route]. */
    fun fixtureResponse(path: String, code: Int = HTTP_OK): MockResponse =
        MockResponse().setResponseCode(code).setBody(Fixtures.read(path)).withHeaders(emptyMap())

    fun takeRequest(): RecordedRequest = server.takeRequest()

    val requestCount: Int get() = server.requestCount

    private fun MockResponse.withHeaders(headers: Map<String, String>): MockResponse = apply {
        setHeader("Content-Type", JSON_CONTENT_TYPE)
        headers.forEach { (name, value) -> setHeader(name, value) }
    }

    private companion object {
        const val HTTP_OK = 200
        const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"
    }
}

/** Recorded API responses on the test classpath, under `src/test/resources/fixtures/`. */
object Fixtures {

    fun read(path: String): String {
        val resource = "fixtures/${path.removePrefix("/")}"
        val stream = Fixtures::class.java.classLoader?.getResourceAsStream(resource)
            ?: error("Missing test fixture '$resource': add it under src/test/resources/")
        return stream.bufferedReader().use { it.readText() }
    }
}
