package com.radustavila.nasaexplorer.feature.launches.details

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.radustavila.nasaexplorer.core.model.BoosterLanding
import com.radustavila.nasaexplorer.core.model.BoosterStage
import com.radustavila.nasaexplorer.feature.launches.R

/** The detail page's booster section: each booster's serial and flight, and how it lands ("OCISLY, flight 4"). */
@Composable
internal fun BoostersSection(boosters: List<BoosterStage>) {
    DetailsSection(stringResource(R.string.launches_details_boosters)) {
        boosters.forEach { booster ->
            Column {
                Text(booster.title(), style = MaterialTheme.typography.titleSmall)
                Text(booster.landing.text(), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun BoosterStage.title(): String {
    val serial = serial ?: return stringResource(R.string.launches_details_booster_unassigned)
    val flight = flightNumber
    return if (flight == null || flight <= 1) {
        stringResource(R.string.launches_details_booster_new, serial)
    } else {
        stringResource(R.string.launches_details_booster_flight, serial, flight)
    }
}

/** "Landed on Of Course I Still Love You (OCISLY)", "Expended, no landing", … */
@Composable
private fun BoosterLanding?.text(): String {
    val place = this?.place()
    return when {
        this == null || attempt == null -> stringResource(R.string.launches_details_landing_unknown)
        attempt == false -> stringResource(R.string.launches_details_landing_expended)
        place == null -> stringResource(R.string.launches_details_landing_unknown)
        success == true -> stringResource(R.string.launches_details_landing_success, place)
        success == false -> stringResource(R.string.launches_details_landing_failure, place)
        else -> stringResource(R.string.launches_details_landing_planned, place)
    }
}

/** Where it lands, by name and abbreviation when Launch Library 2 has both. */
@Composable
private fun BoosterLanding.place(): String? {
    val name = locationName
    val abbrev = locationAbbrev
    return if (name != null && abbrev != null && name != abbrev) {
        stringResource(R.string.launches_details_name_abbrev, name, abbrev)
    } else {
        name ?: abbrev ?: typeName
    }
}
