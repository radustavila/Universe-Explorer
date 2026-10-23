package com.radustavila.nasaexplorer.feature.milkyway

import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.LocationSource
import com.radustavila.nasaexplorer.core.model.MilkyWayOutlook
import com.radustavila.nasaexplorer.core.model.ObserverLocation
import com.radustavila.nasaexplorer.core.testing.data.TestMilkyWay
import java.time.Duration

/** Planner states over [TestMilkyWay]'s Madrid nights, times in UTC. */
internal object MilkyWayFixture {

    private val madrid = ObserverLocation(TestMilkyWay.MADRID, "Madrid", LocationSource.MANUAL)

    val planner = MilkyWayUiState(
        isLocationLoaded = true,
        location = madrid,
        outlook = MilkyWayOutlook(TestMilkyWay.MADRID, TestMilkyWay.nights(), TestMilkyWay.SEASON),
    )

    /** Tonight has no window (the first night is dropped), so the next good night leads. */
    val notTonight = planner.copy(
        outlook = MilkyWayOutlook(
            TestMilkyWay.MADRID,
            listOf(TestMilkyWay.night(0).copy(window = null)) + TestMilkyWay.nights().drop(1),
            TestMilkyWay.SEASON,
        ),
    )

    /** Tromsø at midsummer: never dark, and the core never rises. */
    val polarSummer = MilkyWayUiState(
        isLocationLoaded = true,
        location = ObserverLocation(GeoCoordinate(69.65, 18.96), "Tromsø", LocationSource.MANUAL),
        outlook = MilkyWayOutlook(GeoCoordinate(69.65, 18.96), polarDays(), emptySet()),
    )

    /** 50° north in June: the core clears 10°, but the sky never gets dark enough. */
    val tooLight = MilkyWayUiState(
        isLocationLoaded = true,
        location = ObserverLocation(GeoCoordinate(50.0, 5.0), null, LocationSource.MANUAL),
        outlook = MilkyWayOutlook(GeoCoordinate(50.0, 5.0), polarDays(), TestMilkyWay.SEASON),
    )

    val noPlace = MilkyWayUiState(isLocationLoaded = true)

    private fun polarDays() = List(30) { day ->
        TestMilkyWay.polarDay(TestMilkyWay.FIRST_SUNSET.plus(Duration.ofDays(day.toLong())))
    }
}
