package com.radustavila.nasaexplorer.core.testing.http

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Holds each response of a [FixtureServer.route] until [requests] requests have arrived, so a test
 * can tell calls sent together from calls sent one after another: with serial calls the first one
 * waits in vain and [allArrivedTogether] turns false.
 *
 * ```
 * val gate = ConcurrencyGate(requests = 3)
 * server.route { request -> gate.awaitOthers(); answerFor(request) }
 * ```
 */
class ConcurrencyGate(requests: Int, private val timeoutMillis: Long = 2_000) {

    private val arrived = CountDownLatch(requests)

    @Volatile
    var allArrivedTogether: Boolean = true
        private set

    fun awaitOthers() {
        arrived.countDown()
        if (!arrived.await(timeoutMillis, TimeUnit.MILLISECONDS)) allArrivedTogether = false
    }
}
