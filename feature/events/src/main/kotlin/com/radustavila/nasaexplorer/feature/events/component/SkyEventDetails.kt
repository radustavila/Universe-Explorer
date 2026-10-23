package com.radustavila.nasaexplorer.feature.events.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.skyevent.skyEventTitle
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.designsystem.units.formatLocalDateTime
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.designsystem.util.formatMedium
import com.radustavila.nasaexplorer.core.domain.sky.SkyEventReminderLead
import com.radustavila.nasaexplorer.core.model.CometPerihelion
import com.radustavila.nasaexplorer.core.model.GreatestElongation
import com.radustavila.nasaexplorer.core.model.LunarEclipse
import com.radustavila.nasaexplorer.core.model.MeteorShowerPeak
import com.radustavila.nasaexplorer.core.model.MoonPhaseEvent
import com.radustavila.nasaexplorer.core.model.MoonSize
import com.radustavila.nasaexplorer.core.model.PlanetOpposition
import com.radustavila.nasaexplorer.core.model.PlanetTransit
import com.radustavila.nasaexplorer.core.model.SeasonEvent
import com.radustavila.nasaexplorer.core.model.SkyEvent
import com.radustavila.nasaexplorer.core.model.SolarEclipse
import com.radustavila.nasaexplorer.core.model.SolarEclipseType
import com.radustavila.nasaexplorer.feature.events.R
import kotlin.math.roundToInt

const val SKY_EVENT_REMINDER_SWITCH_TEST_TAG = "sky-event-reminder"
const val SKY_EVENT_COMET_DETAILS_TEST_TAG = "sky-event-comet-details"

/**
 * Everything the calendar knows about one event: when (in the user's time zone and clock format),
 * what to expect from their place, and the reminder switch while the event is still ahead.
 */
@Composable
internal fun SkyEventDetails(
    event: SkyEvent,
    hasPlace: Boolean,
    isPast: Boolean,
    reminderOn: Boolean,
    reminderLead: SkyEventReminderLead?,
    onReminderClick: () -> Unit,
    onLeadChange: (SkyEventReminderLead) -> Unit,
    onCometClick: (designation: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val units = rememberUnitFormatter()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SkyEventIcon(event, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(skyEventTitle(event), style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = event.listedTime.formatLocalDateTime(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        HorizontalDivider()
        when (event) {
            is SolarEclipse -> SolarEclipseDetails(event, hasPlace, units)
            is LunarEclipse -> LunarEclipseDetails(event, hasPlace, units)
            is MoonPhaseEvent -> MoonDetails(event, units)
            is PlanetOpposition -> OppositionDetails(event, units)
            is GreatestElongation -> ElongationDetails(event, units)
            is PlanetTransit -> TransitDetails(event, hasPlace, units)
            is SeasonEvent -> Line(stringResource(event.marker.meaningRes))
            is MeteorShowerPeak -> MeteorDetails(event, units)
            is CometPerihelion -> CometDetails(event, units, onCometClick)
        }
        if (!isPast) {
            HorizontalDivider()
            ReminderControls(reminderOn, reminderLead, onReminderClick, onLeadChange)
        }
    }
}

@Composable
private fun ColumnScope.CometDetails(
    event: CometPerihelion,
    units: UnitFormatter,
    onCometClick: (designation: String) -> Unit,
) {
    Line(stringResource(R.string.sky_events_comet_perihelion_meaning))
    Line(event.name)
    Line(stringResource(R.string.sky_events_comet_sun_distance, units.number(event.perihelionDistanceAu, 2)))
    event.magnitude?.let { magnitude ->
        Line(stringResource(R.string.sky_events_comet_magnitude, units.number(magnitude, 1)))
        Warning(stringResource(R.string.sky_events_comet_prediction))
    }
    TextButton(
        onClick = { onCometClick(event.designation) },
        modifier = Modifier.testTag(SKY_EVENT_COMET_DETAILS_TEST_TAG),
    ) {
        Text(stringResource(R.string.sky_events_comet_details))
    }
}

@Composable
private fun ColumnScope.SolarEclipseDetails(event: SolarEclipse, hasPlace: Boolean, units: UnitFormatter) {
    val local = event.local
    if (local != null) {
        SectionLabel(stringResource(R.string.sky_events_from_your_place))
        skyEventSummary(event, hasPlace = true, units)?.let { Line(it) }
        if (local.type == SolarEclipseType.PARTIAL) {
            Line(stringResource(R.string.sky_events_sun_covered, local.obscuration.percent()))
        }
        val annular = local.type == SolarEclipseType.ANNULAR
        Timeline(
            units,
            R.string.sky_events_partial_begins to local.partialStart,
            (if (annular) R.string.sky_events_annular_begins else R.string.sky_events_total_begins) to local.totalStart,
            R.string.sky_events_maximum to local.peak,
            (if (annular) R.string.sky_events_annular_ends else R.string.sky_events_total_ends) to local.totalEnd,
            R.string.sky_events_partial_ends to local.partialEnd,
        )
        Line(stringResource(R.string.sky_events_sun_altitude, local.sunAltitudeAtPeak.roundToInt()))
        if (local.sunAltitudeAtPeak < LOW_ALTITUDE_DEGREES) Warning(stringResource(R.string.sky_events_sun_low))
    } else {
        val reason = if (hasPlace) R.string.sky_events_not_visible_here else R.string.sky_events_solar_somewhere
        Line(stringResource(reason))
    }
    event.greatestEclipseAt?.let { at ->
        Line(stringResource(R.string.sky_events_greatest_at, units.coordinate(at.latitude, at.longitude)))
    }
    Warning(stringResource(R.string.sky_events_solar_safety))
}

@Composable
private fun ColumnScope.LunarEclipseDetails(event: LunarEclipse, hasPlace: Boolean, units: UnitFormatter) {
    val local = event.local
    when {
        local == null -> Line(stringResource(R.string.sky_events_lunar_somewhere))
        local.isVisible && local.moonAltitudeAtPeak > 0 -> {
            Line(stringResource(R.string.sky_events_lunar_visible_here))
            Line(stringResource(R.string.sky_events_moon_altitude, local.moonAltitudeAtPeak.roundToInt()))
        }

        local.isVisible -> Line(stringResource(R.string.sky_events_lunar_visible_here))
        hasPlace -> Line(stringResource(R.string.sky_events_moon_below))
        else -> Line(stringResource(R.string.sky_events_lunar_somewhere))
    }
    Timeline(
        units,
        R.string.sky_events_penumbral_begins to event.penumbralStart,
        R.string.sky_events_partial_begins to event.partialStart,
        R.string.sky_events_total_begins to event.totalStart,
        R.string.sky_events_maximum to event.time,
        R.string.sky_events_total_ends to event.totalEnd,
        R.string.sky_events_partial_ends to event.partialEnd,
        R.string.sky_events_penumbral_ends to event.penumbralEnd,
    )
}

@Composable
private fun ColumnScope.MoonDetails(event: MoonPhaseEvent, units: UnitFormatter) {
    Line(stringResource(R.string.sky_events_moon_distance, units.distance(event.distanceKm)))
    when (event.size) {
        MoonSize.SUPERMOON -> Line(stringResource(R.string.sky_events_supermoon_meaning))
        MoonSize.MICROMOON -> Line(stringResource(R.string.sky_events_micromoon_meaning))
        null -> Unit
    }
}

@Composable
private fun ColumnScope.OppositionDetails(event: PlanetOpposition, units: UnitFormatter) {
    Line(stringResource(R.string.sky_events_opposition_meaning))
    Line(stringResource(R.string.sky_events_planet_details, units.number(event.magnitude, 1), event.constellation))
    Line(stringResource(R.string.sky_events_distance_au, units.number(event.distanceAu, 2)))
}

@Composable
private fun ColumnScope.ElongationDetails(event: GreatestElongation, units: UnitFormatter) {
    Line(stringResource(event.apparition.labelRes))
    Line(stringResource(R.string.sky_events_elongation_angle, units.number(event.elongationDegrees, 1)))
}

@Composable
private fun ColumnScope.TransitDetails(event: PlanetTransit, hasPlace: Boolean, units: UnitFormatter) {
    if (hasPlace && event.isVisibleLocally == false) Line(stringResource(R.string.sky_events_not_visible_here))
    Timeline(
        units,
        R.string.sky_events_transit_begins to event.transitStart,
        R.string.sky_events_maximum to event.time,
        R.string.sky_events_transit_ends to event.transitEnd,
    )
    Warning(stringResource(R.string.sky_events_transit_safety))
}

@Composable
private fun ColumnScope.MeteorDetails(event: MeteorShowerPeak, units: UnitFormatter) {
    Line(pluralStringResource(R.plurals.sky_events_meteor_rate, event.zenithalHourlyRate, event.zenithalHourlyRate))
    Line(stringResource(R.string.sky_events_meteor_moon, event.moonIlluminatedFraction.percent()))
    if (event.moonIlluminatedFraction > BRIGHT_MOON) Warning(stringResource(R.string.sky_events_meteor_moon_bright))
    event.viewing?.let { viewing ->
        SectionLabel(stringResource(R.string.sky_events_from_your_place))
        Line(stringResource(
            R.string.sky_events_meteor_dark,
            units.dayAndTime(viewing.darkFrom),
            units.time(viewing.darkUntil),
        ))
        Line(
            stringResource(
                R.string.sky_events_meteor_best,
                units.time(viewing.bestTime),
                viewing.radiantAltitudeDegrees.roundToInt(),
                stringResource(compassPoint(viewing.radiantAzimuthDegrees)),
            ),
        )
        if (viewing.radiantAltitudeDegrees < LOW_RADIANT_DEGREES) {
            Warning(stringResource(R.string.sky_events_meteor_radiant_low))
        }
    }
    SectionLabel(stringResource(R.string.sky_events_details))
    Line(stringResource(R.string.sky_events_meteor_radiant, event.radiantConstellation))
    Line(stringResource(R.string.sky_events_meteor_parent, event.parentBody))
    Line(stringResource(
        R.string.sky_events_meteor_active,
        event.activeFrom.formatMedium(),
        event.activeUntil.formatMedium(),
    ))
}

private const val LOW_ALTITUDE_DEGREES = 10.0

/** Below this the radiant's meteors mostly fall under the horizon. */
private const val LOW_RADIANT_DEGREES = 20.0
private const val BRIGHT_MOON = 0.5
