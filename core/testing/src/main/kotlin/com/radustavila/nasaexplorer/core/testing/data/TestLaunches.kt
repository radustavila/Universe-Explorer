package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.Launch
import com.radustavila.nasaexplorer.core.model.LaunchStatus
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
    ): Launch = Launch(
        id = id,
        name = name,
        net = net,
        status = status,
        rocket = "Falcon 9 Block 5",
        pad = "Space Launch Complex 40, Cape Canaveral SFS, FL, USA",
        imageUrl = "https://example.com/falcon9.jpg",
        livestreamUrl = livestreamUrl,
        pageUrl = "https://spacelaunchnow.app/launch/$id/",
    )

    /** [size] launches a day apart, soonest first, all still ahead of [NOW]. */
    fun upcoming(size: Int): List<Launch> = (1..size).map { index ->
        launch(
            id = "launch-$index",
            name = "Rocket $index | Mission $index",
            net = NOW.plus(Duration.ofDays(index.toLong())),
        )
    }
}
