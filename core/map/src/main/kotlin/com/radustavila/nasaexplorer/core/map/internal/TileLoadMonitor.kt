package com.radustavila.nasaexplorer.core.map.internal

import com.radustavila.nasaexplorer.core.map.TileLoadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Outcome of the latest GIBS tile request. Process-wide because MapLibre's HTTP stack is: every map
 * shares one OkHttp client. Only one map is on screen at a time, so that is precise enough.
 */
internal object TileLoadMonitor {
    private val mutableStatus = MutableStateFlow(TileLoadStatus.Loaded)
    val status: StateFlow<TileLoadStatus> = mutableStatus.asStateFlow()

    fun report(outcome: TileLoadStatus) {
        mutableStatus.value = outcome
    }

    /** Forgets the previous imagery's failures when the map switches layer or date. */
    fun reset() {
        mutableStatus.value = TileLoadStatus.Loaded
    }
}
