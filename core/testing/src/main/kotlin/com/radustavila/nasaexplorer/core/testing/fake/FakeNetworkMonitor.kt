package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.domain.connectivity.NetworkMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeNetworkMonitor(initiallyOnline: Boolean = true) : NetworkMonitor {

    private val online = MutableStateFlow(initiallyOnline)

    override val isOnline: Flow<Boolean> = online

    fun setOnline(isOnline: Boolean) {
        online.value = isOnline
    }
}
