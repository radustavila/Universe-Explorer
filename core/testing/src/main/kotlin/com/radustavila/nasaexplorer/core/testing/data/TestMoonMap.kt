package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.LunarLandingKind
import com.radustavila.nasaexplorer.core.model.LunarLandingSite
import com.radustavila.nasaexplorer.core.model.LunarProgramme
import com.radustavila.nasaexplorer.core.model.SelenographicPoint
import java.time.LocalDate

/** A few landing sites from the bundled table, oldest first. */
object TestMoonMap {
    val luna9 = LunarLandingSite(
        id = "luna-9",
        mission = "Luna 9",
        programme = LunarProgramme.LUNA,
        kind = LunarLandingKind.LANDER,
        landedOn = LocalDate.of(1966, 2, 3),
        position = SelenographicPoint(7.08, -64.37),
        region = "Oceanus Procellarum",
    )

    val apollo11 = LunarLandingSite(
        id = "apollo-11",
        mission = "Apollo 11",
        programme = LunarProgramme.APOLLO,
        kind = LunarLandingKind.CREWED,
        landedOn = LocalDate.of(1969, 7, 20),
        position = SelenographicPoint(0.67416, 23.47314),
        region = "Mare Tranquillitatis",
        crew = listOf("Neil Armstrong", "Buzz Aldrin"),
        nssdcaId = "1969-059C",
    )

    val apollo17 = LunarLandingSite(
        id = "apollo-17",
        mission = "Apollo 17",
        programme = LunarProgramme.APOLLO,
        kind = LunarLandingKind.CREWED,
        landedOn = LocalDate.of(1972, 12, 11),
        position = SelenographicPoint(20.1911, 30.7723),
        region = "Taurus–Littrow",
        crew = listOf("Eugene Cernan", "Harrison Schmitt"),
        nssdcaId = "1972-096C",
    )

    val change4 = LunarLandingSite(
        id = "change-4",
        mission = "Chang'e 4",
        programme = LunarProgramme.CHANGE,
        kind = LunarLandingKind.ROVER,
        landedOn = LocalDate.of(2019, 1, 3),
        position = SelenographicPoint(-45.4446, 177.5991),
        region = "Von Kármán",
        rover = "Yutu-2",
    )

    val sites = listOf(luna9, apollo11, apollo17, change4)
}
