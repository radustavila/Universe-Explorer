package com.radustavila.nasaexplorer.core.domain.sync

/**
 * Scheduler for the EPIC cache. Same contract as the APOD [SyncScheduler], but its own unique work:
 * an EPIC sync neither waits for nor cancels an APOD sync, and each reports its own state.
 *
 * A separate type rather than a qualifier so injecting the wrong scheduler is a compile error.
 */
interface EpicSyncScheduler : SyncScheduler {

    /**
     * Like [requestSync]; with [force] the run ignores EPIC's refresh interval (pull-to-refresh).
     * A host that is cooling down after a rate limit still refuses the call.
     */
    fun requestSync(force: Boolean)
}
