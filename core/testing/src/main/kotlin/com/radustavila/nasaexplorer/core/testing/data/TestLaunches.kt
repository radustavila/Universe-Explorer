package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.BoosterLanding
import com.radustavila.nasaexplorer.core.model.BoosterStage
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.Launch
import com.radustavila.nasaexplorer.core.model.LaunchMission
import com.radustavila.nasaexplorer.core.model.LaunchStatus
import com.radustavila.nasaexplorer.core.model.LaunchTimeline
import com.radustavila.nasaexplorer.core.model.NetPrecision
import com.radustavila.nasaexplorer.core.model.Webcast
import java.time.Duration
import java.time.Instant

object TestLaunches {

    val NOW: Instant = Instant.parse("2026-10-19T10:00:00Z")

    fun launch(
        id: String = "3b430f81-0ff1-4d85-8a1a-cef0de326198",
        name: String = "Falcon 9 Block 5 | Bandwagon 5",
        net: Instant = NOW.plus(Duration.ofHours(2)),
        status: LaunchStatus = LaunchStatus.GO,
        livestreamUrl: String? = "https://x.com/i/broadcasts/1nxeLMddZlEJX",
        provider: String? = "SpaceX",
        netPrecision: NetPrecision = NetPrecision.MINUTE,
        crewCount: Int = 0,
        boosters: List<BoosterStage> = emptyList(),
        timeline: LaunchTimeline = LaunchTimeline.UPCOMING,
        failReason: String? = null,
    ): Launch = Launch(
        id = id,
        name = name,
        net = net,
        status = status,
        rocket = "Falcon 9 Block 5",
        pad = "Space Launch Complex 40, Cape Canaveral SFS, FL, USA",
        provider = provider,
        imageUrl = "https://example.com/falcon9.jpg",
        livestreamUrl = livestreamUrl,
        pageUrl = "https://spacelaunchnow.app/launch/$id/",
        netPrecision = netPrecision,
        crewCount = crewCount,
        boosters = boosters,
        timeline = timeline,
        failReason = failReason,
    )

    /** A Go Falcon 9 with everything the detail page shows: window, weather, mission, pad, landing, streams. */
    fun detailed(
        id: String = "3b430f81-0ff1-4d85-8a1a-cef0de326198",
        net: Instant = NOW.plus(Duration.ofHours(2)),
        status: LaunchStatus = LaunchStatus.GO,
        timeline: LaunchTimeline = LaunchTimeline.UPCOMING,
        landingSuccess: Boolean? = null,
    ): Launch = launch(id = id, net = net, status = status, timeline = timeline).copy(
        windowStart = net.minus(Duration.ofMinutes(5)),
        windowEnd = net.plus(Duration.ofHours(1)),
        probability = 85,
        mission = LaunchMission(
            name = "Bandwagon 5",
            description = "A rideshare mission to a mid-inclination low Earth orbit.",
            type = "Dedicated Rideshare",
            orbitName = "Low Earth Orbit",
            orbitAbbrev = "LEO",
        ),
        padPosition = GeoCoordinate(latitude = 28.5618571, longitude = -80.577366),
        rocketName = "Falcon 9",
        rocketVariant = "Block 5",
        boosters = listOf(
            BoosterStage(
                serial = "B1090",
                flightNumber = 4,
                type = "Core",
                landing = BoosterLanding(
                    attempt = true,
                    success = landingSuccess,
                    locationName = "Of Course I Still Love You",
                    locationAbbrev = "OCISLY",
                    typeName = "Autonomous Spaceport Drone Ship",
                    typeAbbrev = "ASDS",
                ),
            ),
        ),
        webcasts = listOf(
            Webcast(url = "https://www.youtube.com/watch?v=abc", title = "Bandwagon 5 Mission", publisher = "SpaceX"),
        ),
    )

    /** [size] launches a day apart, soonest first, all still ahead of [NOW]. */
    fun upcoming(size: Int): List<Launch> = (1..size).map { index ->
        launch(
            id = "launch-$index",
            name = "Rocket $index | Mission $index",
            net = NOW.plus(Duration.ofDays(index.toLong())),
        )
    }

    /** [size] launches a day apart, most recent first, all flown before [NOW]. */
    fun past(size: Int): List<Launch> = (1..size).map { index ->
        launch(
            id = "past-$index",
            name = "Rocket $index | Past mission $index",
            net = NOW.minus(Duration.ofDays(index.toLong())),
            status = LaunchStatus.SUCCESS,
            timeline = LaunchTimeline.PAST,
            livestreamUrl = null,
        )
    }
}
