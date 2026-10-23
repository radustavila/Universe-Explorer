package com.radustavila.nasaexplorer.data.earthevents.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import com.radustavila.nasaexplorer.core.testing.data.TestEarthEvents
import com.radustavila.nasaexplorer.core.testing.data.TestEarthquakes
import com.radustavila.nasaexplorer.data.earthevents.local.EarthEventsDatabase
import com.radustavila.nasaexplorer.data.earthevents.local.RoomEarthEventsLocalDataSource
import com.radustavila.nasaexplorer.data.earthevents.local.RoomEarthquakesLocalDataSource
import com.radustavila.nasaexplorer.data.earthevents.remote.EarthquakeFeed
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EarthEventsCacheEvictorTest {

    private lateinit var database: EarthEventsDatabase
    private lateinit var local: RoomEarthEventsLocalDataSource
    private lateinit var evictor: EarthEventsCacheEvictor
    private val now = TestEarthEvents.NOW

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            EarthEventsDatabase::class.java,
        ).allowMainThreadQueries().build()
        val io = UnconfinedTestDispatcher()
        local = RoomEarthEventsLocalDataSource(database.earthEventDao(), io)
        evictor = EarthEventsCacheEvictor(database.earthEventDao(), database.earthquakeDao(), io)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `events with no report in the active window go, recent ones stay`() = runTest {
        val recent = TestEarthEvents.wildfire(id = "recent", at = now.minus(Duration.ofDays(2)))
        val stale = TestEarthEvents.wildfire(id = "stale", at = now.minus(EarthEventsCacheEvictor.EVENT_RETENTION).minusSeconds(1))
        local.replaceEvents(listOf(recent, stale), at = now)

        val evicted = evictor.evict(now)

        assertEquals(1, evicted)
        assertEquals(listOf("recent"), local.observeEvents().first().map { it.id })
    }

    @Test
    fun `imagery layers are dropped once they are a month old`() = runTest {
        local.replaceLayers(
            EarthEventCategory.WILDFIRES,
            listOf(TestEarthEvents.TRUE_COLOR_LAYER),
            at = now.minus(EarthEventsCacheEvictor.LAYER_RETENTION).minusSeconds(1),
        )
        local.replaceLayers(EarthEventCategory.FLOODS, listOf(TestEarthEvents.TRUE_COLOR_LAYER), at = now)

        assertEquals(1, evictor.evict(now))
        assertEquals(DataResult.Success(emptyList<Any>()), local.layersFor(EarthEventCategory.WILDFIRES))
        assertEquals(DataResult.Success(listOf(TestEarthEvents.TRUE_COLOR_LAYER)), local.layersFor(EarthEventCategory.FLOODS))
    }

    @Test
    fun `quakes older than the longest feed window go`() = runTest {
        val quakes = RoomEarthquakesLocalDataSource(database.earthquakeDao(), UnconfinedTestDispatcher())
        val recent = TestEarthquakes.earthquake(id = "recent", time = now.minus(Duration.ofDays(3)))
        val tooOld = now.minus(EarthEventsCacheEvictor.EARTHQUAKE_RETENTION).minusSeconds(1)
        val old = TestEarthquakes.significant(id = "old", time = tooOld)
        quakes.replaceFeed(EarthquakeFeed.SIGNIFICANT_MONTH, listOf(recent, old), lastModified = null, at = now)

        assertEquals(1, evictor.evict(now))
        assertEquals(listOf("recent"), quakes.observeEarthquakes().first().map { it.id })
    }
}
