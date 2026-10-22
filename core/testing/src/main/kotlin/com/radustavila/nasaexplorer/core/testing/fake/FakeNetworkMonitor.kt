package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeNetworkMonitor(initiallyOnline: Boolean = true) : NetworkMonitor {

    private val online = MutableStateFlow(initiallyOnline)

    override val isOnline: Flow<Boolean> = online

    /** What [isUnmetered] answers; a metered (mobile) network by default. */
    var unmetered: Boolean = false

    override suspend fun isUnmetered(): Boolean = unmetered

    fun setOnline(isOnline: Boolean) {
        online.value = isOnline
    }
}
