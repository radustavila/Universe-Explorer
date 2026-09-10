package com.radustavila.nasaexplorer.core.domain.connectivity

import kotlinx.coroutines.flow.Flow

/**
 * Connectivity as a cold [Flow] of booleans. The Android `ConnectivityManager` implementation lives
 * in `:core:network`; the domain layer only sees this interface.
 */
interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}
