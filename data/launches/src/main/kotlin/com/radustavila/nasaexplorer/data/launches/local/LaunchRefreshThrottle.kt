package com.radustavila.nasaexplorer.data.launches.local

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps the app inside Launch Library 2's anonymous budget (15 calls an hour per IP) by allowing at
 * most one refresh per [MIN_REFRESH_INTERVAL], persisted so app restarts do not reset it.
 *
 * Deliberately local to the launches module: a shared per-host rate limiter in `:core:network` can
 * replace it without touching the repository's contract.
 */
interface LaunchRefreshThrottle {

    /**
     * Claims the next call. Returns `false` when the previous one was too recent and the caller must
     * skip the network. A claimed call counts even if it then fails: LL2 counts it too.
     */
    suspend fun tryAcquire(): Boolean

    /** Pushes the next allowed call out to at least [instant], e.g. after a 429's `Retry-After`. */
    suspend fun deferUntil(instant: Instant)

    companion object {
        val MIN_REFRESH_INTERVAL: Duration = Duration.ofMinutes(60)
    }
}

@Singleton
class RoomLaunchRefreshThrottle @Inject constructor(
    private val launchDao: LaunchDao,
    private val timeProvider: TimeProvider,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : LaunchRefreshThrottle {

    // The on-demand and the periodic sync may run at the same time; only one of them may call.
    private val mutex = Mutex()

    override suspend fun tryAcquire(): Boolean = guarded(fallback = false) {
        val now = timeProvider.now()
        val nextAllowedAt = launchDao.nextAllowedRefreshAt()?.let(Instant::ofEpochMilli)
        if (nextAllowedAt != null && now < nextAllowedAt && !nextAllowedAt.isImplausiblyFar(now)) {
            false
        } else {
            launchDao.setNextAllowedAt(now + LaunchRefreshThrottle.MIN_REFRESH_INTERVAL)
            true
        }
    }

    override suspend fun deferUntil(instant: Instant) = guarded(fallback = Unit) {
        val current = launchDao.nextAllowedRefreshAt()
        if (current == null || instant.toEpochMilli() > current) {
            launchDao.setNextAllowedAt(instant)
        }
    }

    /**
     * A deadline further away than any interval or `Retry-After` the app would set means the device
     * clock moved backwards; waiting it out could hide fresh launches for days.
     */
    private fun Instant.isImplausiblyFar(now: Instant): Boolean =
        Duration.between(now, this) > MAX_PLAUSIBLE_WAIT

    private suspend fun LaunchDao.setNextAllowedAt(instant: Instant) =
        setRefreshState(LaunchRefreshEntity(nextAllowedAtEpochMillis = instant.toEpochMilli()))

    /** A broken bookkeeping table must not crash the sync; failing closed keeps the budget safe. */
    private suspend fun <T> guarded(fallback: T, block: suspend () -> T): T =
        withContext(ioDispatcher) {
            mutex.withLock {
                try {
                    block()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    fallback
                }
            }
        }

    private companion object {
        val MAX_PLAUSIBLE_WAIT: Duration = Duration.ofHours(6)
    }
}
