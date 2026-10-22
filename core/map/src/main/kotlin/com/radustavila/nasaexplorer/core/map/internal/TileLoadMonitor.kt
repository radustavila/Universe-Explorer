package com.radustavila.nasaexplorer.core.map.internal

import com.radustavila.nasaexplorer.core.map.TileLoadStatus
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Routes the outcome of each GIBS tile request to the [TileTally]s watching its URL. Process-wide
 * because MapLibre's HTTP stack is: every map shares one OkHttp client.
 */
internal object TileLoadMonitor {
    private val tallies = CopyOnWriteArraySet<TileTally>()

    /** Starts counting the requests whose URL starts with one of [urlPrefixes]. */
    fun watch(urlPrefixes: List<String>): TileTally = TileTally(urlPrefixes).also(tallies::add)

    /** Stops counting for [tally]; what it counted so far stays readable. */
    fun unwatch(tally: TileTally) {
        tallies.remove(tally)
    }

    fun report(url: String, outcome: TileLoadStatus) {
        tallies.forEach { if (it.matches(url)) it.record(outcome) }
    }
}

/** Tile requests of one imagery stack: how many loaded or failed, and the latest outcome. */
internal class TileTally(private val urlPrefixes: List<String>) {
    private val loadedCount = AtomicInteger()
    private val failedCount = AtomicInteger()
    private val mutableStatus = MutableStateFlow(TileLoadStatus.Loaded)

    /** Outcome of the latest request; [TileLoadStatus.Loaded] before any. */
    val status: StateFlow<TileLoadStatus> = mutableStatus.asStateFlow()

    val loaded: Int get() = loadedCount.get()
    val failed: Int get() = failedCount.get()

    /**
     * Whether imagery that came back like this may replace the imagery on screen: some tile
     * arrived, or nothing failed (tiles served from MapLibre's cache never reach OkHttp).
     */
    val canReplaceShown: Boolean get() = loaded > 0 || failed == 0

    fun matches(url: String): Boolean = urlPrefixes.any(url::startsWith)

    fun record(outcome: TileLoadStatus) {
        if (outcome == TileLoadStatus.Loaded) loadedCount.incrementAndGet() else failedCount.incrementAndGet()
        mutableStatus.value = outcome
    }
}
