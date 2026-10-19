package com.radustavila.nasaexplorer.core.testing.fake

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.domain.sky.IssPassNotifier
import com.radustavila.nasaexplorer.core.domain.sky.IssPassReminderScheduler
import com.radustavila.nasaexplorer.core.domain.sky.PeopleInSpaceRepository
import com.radustavila.nasaexplorer.core.domain.sky.SatellitePassPredictor
import com.radustavila.nasaexplorer.core.domain.sky.SatelliteRepository
import com.radustavila.nasaexplorer.core.domain.sky.SkyCalculator
import com.radustavila.nasaexplorer.core.domain.sky.SkyPreferencesRepository
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.OrbitalElements
import com.radustavila.nasaexplorer.core.model.PersonInSpace
import com.radustavila.nasaexplorer.core.model.SatellitePass
import com.radustavila.nasaexplorer.core.model.SatellitePosition
import com.radustavila.nasaexplorer.core.model.SkyTonight
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Returns [sky] for any place and time, recording what it was asked. */
class FakeSkyCalculator(var sky: SkyTonight = TestSky.skyTonight()) : SkyCalculator {

    val requests = mutableListOf<Pair<GeoCoordinate, Instant>>()

    override fun skyTonight(observer: GeoCoordinate, now: Instant): SkyTonight {
        requests += observer to now
        return sky
    }
}

/** Returns [passes] filtered to the requested window, and [position] / [track] as set. */
class FakeSatellitePassPredictor(
    var passes: List<SatellitePass> = emptyList(),
    var position: SatellitePosition? = null,
    var track: List<SatellitePosition> = emptyList(),
) : SatellitePassPredictor {

    override fun passes(
        elements: OrbitalElements,
        observer: GeoCoordinate,
        from: Instant,
        to: Instant,
        minElevationDegrees: Double,
    ): List<SatellitePass> = passes.filter { it.end.isAfter(from) && !it.peak.isAfter(to) }

    override fun position(elements: OrbitalElements, at: Instant): SatellitePosition? = position?.copy(at = at)

    override fun groundTrack(
        elements: OrbitalElements,
        from: Instant,
        to: Instant,
        step: Duration,
    ): List<SatellitePosition> = track
}

class FakeSatelliteRepository : SatelliteRepository {

    private val elements = MutableStateFlow<Map<Int, OrbitalElements>>(emptyMap())

    var refreshResult: DataResult<Unit> = DataResult.Success(Unit)

    /** Stored on a successful refresh, as the real repository would. */
    var elementsOnRefresh: OrbitalElements? = null

    var livePosition: DataResult<SatellitePosition> = DataResult.Failure(AppError.NoConnection)

    var refreshCallCount: Int = 0
        private set

    var livePositionCallCount: Int = 0
        private set

    fun setElements(value: OrbitalElements?) {
        elements.value = if (value == null) emptyMap() else mapOf(value.noradId to value)
    }

    override fun observeElements(noradId: Int): Flow<OrbitalElements?> = elements.map { it[noradId] }

    override suspend fun refreshElements(): DataResult<Unit> {
        refreshCallCount++
        if (refreshResult is DataResult.Success) elementsOnRefresh?.let(::setElements)
        return refreshResult
    }

    override suspend fun fetchLivePosition(noradId: Int): DataResult<SatellitePosition> {
        livePositionCallCount++
        return livePosition
    }
}

class FakePeopleInSpaceRepository : PeopleInSpaceRepository {

    private val people = MutableStateFlow<List<PersonInSpace>>(emptyList())

    var refreshResult: DataResult<Int> = DataResult.Success(0)

    var refreshCallCount: Int = 0
        private set

    fun setPeople(value: List<PersonInSpace>) {
        people.value = value
    }

    override fun observePeopleInSpace(): Flow<List<PersonInSpace>> = people

    override suspend fun refreshPeopleInSpace(): DataResult<Int> {
        refreshCallCount++
        return refreshResult
    }
}

class FakeSkyPreferencesRepository : SkyPreferencesRepository {

    private val enabled = MutableStateFlow(false)
    private val lastReminded = MutableStateFlow<Instant?>(null)

    override val issPassRemindersEnabled: Flow<Boolean> = enabled

    override suspend fun setIssPassRemindersEnabled(enabled: Boolean) {
        this.enabled.value = enabled
    }

    override val lastRemindedPassStart: Flow<Instant?> = lastReminded

    override suspend fun setLastRemindedPassStart(start: Instant) {
        lastReminded.value = start
    }
}

/** Remembers the pending wake-up; null after [cancel]. */
class FakeIssPassReminderScheduler : IssPassReminderScheduler {

    var scheduledAt: Instant? = null
        private set

    override fun scheduleAt(at: Instant) {
        scheduledAt = at
    }

    override fun cancel() {
        scheduledAt = null
    }
}

class FakeIssPassNotifier : IssPassNotifier {

    val notified = mutableListOf<SatellitePass>()

    override suspend fun notifyUpcomingPass(pass: SatellitePass) {
        notified += pass
    }
}
