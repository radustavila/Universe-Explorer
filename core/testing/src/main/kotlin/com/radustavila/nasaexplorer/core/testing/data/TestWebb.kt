package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.WebbSchedule
import com.radustavila.nasaexplorer.core.model.WebbVisit
import java.time.Duration
import java.time.Instant

/** Webb visits for tests, modelled on STScI's schedule for the week of 19 October 2026. */
object TestWebb {

    /** During [WASP_39]'s visit, the second of [week]. */
    val NOW: Instant = Instant.parse("2026-10-23T09:00:00Z")

    fun visit(
        visitId: String = "1366:4:1",
        start: Instant = Instant.parse("2026-10-23T07:05:00Z"),
        duration: Duration = Duration.parse("PT8H33M"),
        visitType: String = "PRIME TARGETED FIXED",
        instrument: String = "NIRSpec Bright Object Time Series",
        targetName: String? = "WASP-39",
        category: String? = "Star",
        keywords: String? = "Exoplanet Systems",
        parallels: List<String> = emptyList(),
    ) = WebbVisit(
        visitId = visitId,
        start = start,
        duration = duration,
        visitType = visitType,
        instrument = instrument,
        targetName = targetName,
        category = category,
        keywords = keywords,
        parallels = parallels,
    )

    val FINE_PHASING = visit(
        visitId = "12636:166:1",
        start = Instant.parse("2026-10-23T06:29:58Z"),
        duration = Duration.ofMinutes(28).plusSeconds(39),
        visitType = "PRIME WFSC ROUTINE",
        instrument = "WFSC NIRCam Fine Phasing",
        targetName = "2MASS-03313052-2739514-2",
        category = "Calibration",
        keywords = "Focus test",
    )

    val WASP_39 = visit()

    val JUPITER = visit(
        visitId = "4802:12:1",
        start = Instant.parse("2026-10-23T16:55:00Z"),
        duration = Duration.ofHours(2).plusMinutes(5),
        visitType = "PRIME TARGETED MOVING",
        instrument = "NIRCam Imaging",
        targetName = "Jupiter",
        category = "Solar System",
        keywords = "Giant planets",
        parallels = listOf("MIRI Imaging"),
    )

    val DARK = visit(
        visitId = "12526:122:1",
        start = Instant.parse("2026-10-23T19:10:00Z"),
        duration = Duration.ofMinutes(21).plusSeconds(53),
        visitType = "PRIME UNTARGETED",
        instrument = "MIRI Dark",
        targetName = null,
        category = null,
        keywords = null,
    )

    val CARINA = visit(
        visitId = "2731:2:1",
        start = Instant.parse("2026-10-24T02:00:00Z"),
        duration = Duration.ofHours(5),
        instrument = "MIRI Medium Resolution Spectroscopy",
        targetName = "NGC-3324",
        category = "ISM",
        keywords = "Star formation",
    )

    /** Five visits on 23 and 24 October 2026, with a slew after [FINE_PHASING] and longer gaps later. */
    fun week(fetchedAt: Instant = Instant.parse("2026-10-23T05:00:00Z")) = WebbSchedule(
        visits = listOf(FINE_PHASING, WASP_39, JUPITER, DARK, CARINA),
        fetchedAt = fetchedAt,
    )
}
