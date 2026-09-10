package com.radustavila.nasaexplorer.core.domain.sync

import com.radustavila.nasaexplorer.core.model.SyncState
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over *when* synchronisation happens.
 *
 * The presentation layer only ever asks for a sync and observes its state; the WorkManager-based
 * implementation lives in `:data:nasa`. Swapping it for a foreground-only or push-triggered strategy
 * requires no UI change.
 */
interface SyncScheduler {

    /** Current state of the sync pipeline. */
    val syncState: Flow<SyncState>

    /** Requests an immediate sync, subject to the scheduler's constraints (e.g. connectivity). */
    fun requestSync()

    /** Makes sure periodic background synchronisation is registered. Idempotent. */
    fun ensurePeriodicSync()
}
