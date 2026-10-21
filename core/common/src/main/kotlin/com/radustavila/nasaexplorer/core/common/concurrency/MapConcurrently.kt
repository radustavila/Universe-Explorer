package com.radustavila.nasaexplorer.core.common.concurrency

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * [transform]s every element with at most [maxConcurrent] running at once, keeping the input order
 * in the result. For fan-outs to one host (a few dozen requests) that should be quicker than one by
 * one without bursting past its rate limit.
 *
 * Like [coroutineScope], an exception thrown by one transform cancels the rest; transforms that
 * report failures as values (`DataResult`) are all run.
 */
suspend fun <T, R> Iterable<T>.mapConcurrently(maxConcurrent: Int, transform: suspend (T) -> R): List<R> {
    require(maxConcurrent > 0) { "maxConcurrent must be positive" }
    val gate = Semaphore(maxConcurrent)
    return coroutineScope {
        map { element -> async { gate.withPermit { transform(element) } } }.awaitAll()
    }
}
