package com.radustavila.nasaexplorer.core.model

import com.radustavila.nasaexplorer.core.common.error.AppError

/**
 * Observable state of background synchronisation, mirrored from the scheduler (WorkManager) so the
 * UI never has to know what performs the sync.
 */
sealed interface SyncState {
    data object Idle : SyncState
    data object Enqueued : SyncState
    data object Syncing : SyncState
    data object Succeeded : SyncState
    data class Failed(val error: AppError? = null) : SyncState

    val isRunning: Boolean get() = this is Syncing || this is Enqueued
}
