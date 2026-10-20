package com.radustavila.nasaexplorer.core.domain.sync

import com.radustavila.nasaexplorer.core.model.SyncSource
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One [Mutex] per [SyncSource], shared app-wide, so two refreshes of the same source (a worker and
 * a screen opening at the same moment) never run side by side. Different sources do not wait for
 * each other.
 */
@Singleton
class SyncSourceLocks @Inject constructor() {

    private val locks = ConcurrentHashMap<SyncSource, Mutex>()

    suspend fun <T> withLock(source: SyncSource, block: suspend () -> T): T =
        locks.getOrPut(source) { Mutex() }.withLock { block() }
}
