package com.radustavila.nasaexplorer.feature.sky.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.component.NasaAsyncImage
import com.radustavila.nasaexplorer.core.model.MoonTonight
import com.radustavila.nasaexplorer.core.model.NightWindow
import com.radustavila.nasaexplorer.core.model.PersonInSpace
import com.radustavila.nasaexplorer.core.model.PlanetTonight
import com.radustavila.nasaexplorer.feature.sky.R
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

const val SKY_NIGHT_CARD_TEST_TAG = "sky-night-card"
const val SKY_MOON_CARD_TEST_TAG = "sky-moon-card"
const val SKY_PLANETS_CARD_TEST_TAG = "sky-planets-card"
const val SKY_PEOPLE_CARD_TEST_TAG = "sky-people-card"

/** A titled card, the building block of the screen. */
@Composable
internal fun SkySection(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
internal fun NightCard(night: NightWindow, modifier: Modifier = Modifier) {
    SkySection(title = stringResource(R.string.sky_night_title), modifier = modifier.testTag(SKY_NIGHT_CARD_TEST_TAG)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            NightEvent(stringResource(R.string.sky_sunset), night.sunset)
            NightEvent(stringResource(R.string.sky_dark), night.astronomicalDusk ?: night.nauticalDusk)
            NightEvent(stringResource(R.string.sky_dawn), night.astronomicalDawn ?: night.nauticalDawn)
            NightEvent(stringResource(R.string.sky_sunrise), night.sunrise)
        }
        Text(
            text = stringResource(R.string.sky_times_in_zone, ZoneId.systemDefault().id),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NightEvent(label: String, at: Instant?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = at?.formatTime() ?: stringResource(R.string.sky_no_event),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
internal fun MoonCard(moon: MoonTonight, southernHemisphere: Boolean, modifier: Modifier = Modifier) {
    SkySection(title = stringResource(R.string.sky_moon_title), modifier = modifier.testTag(SKY_MOON_CARD_TEST_TAG)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MoonPhaseIcon(
                phaseAngleDegrees = moon.phaseAngleDegrees,
                mirrored = southernHemisphere,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(moon.phase.labelRes()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(stringResource(R.string.sky_moon_illuminated, (moon.illuminatedFraction * PERCENT).roundToInt()))
                Text(riseSetLine(moon.isUpAtStart, moon.rise, moon.set), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            text = if (moon.nextFullMoon.isBefore(moon.nextNewMoon)) {
                stringResource(R.string.sky_moon_next_full, moon.nextFullMoon.formatDay())
            } else {
                stringResource(R.string.sky_moon_next_new, moon.nextNewMoon.formatDay())
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Up at sunset · Sets 00:53" or "Rises 01:00 · Sets 16:03". */
@Composable
private fun riseSetLine(isUpAtStart: Boolean, rise: Instant?, set: Instant?): String {
    val parts = buildList {
        if (isUpAtStart) {
            add(stringResource(R.string.sky_moon_up_at_sunset))
        } else if (rise != null) {
            add(stringResource(R.string.sky_rises_at, rise.formatTime()))
        }
        if (set != null) add(stringResource(R.string.sky_sets_at, set.formatTime()))
    }
    return parts.joinToString(" · ")
}

@Composable
internal fun PlanetsCard(planets: List<PlanetTonight>, modifier: Modifier = Modifier) {
    SkySection(title = stringResource(R.string.sky_planets_title), modifier = modifier.testTag(SKY_PLANETS_CARD_TEST_TAG)) {
        planets.forEachIndexed { index, planet ->
            if (index > 0) HorizontalDivider()
            PlanetRow(planet)
        }
    }
}

@Composable
private fun PlanetRow(planet: PlanetTonight) {
    val dimmed = !planet.isVisibleTonight
    val color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(stringResource(planet.planet.labelRes()), style = MaterialTheme.typography.titleSmall, color = color)
        val bestTime = planet.bestTime
        if (bestTime != null) {
            Text(
                text = stringResource(
                    R.string.sky_planet_best,
                    bestTime.formatTime(),
                    (planet.bestAltitudeDegrees ?: 0.0).roundToInt(),
                    stringResource(compassPointRes(planet.bestAzimuthDegrees ?: 0.0)),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(stringResource(R.string.sky_planet_not_visible), style = MaterialTheme.typography.bodyMedium, color = color)
        }
        Text(
            text = riseSetLine(planet.isUpAtStart, planet.rise, planet.set) + " · " +
                stringResource(R.string.sky_planet_details, planet.magnitude.formatDecimal(1), planet.constellation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun PeopleInSpaceCard(people: List<PersonInSpace>, now: Instant, modifier: Modifier = Modifier) {
    SkySection(title = stringResource(R.string.sky_people_title), modifier = modifier.testTag(SKY_PEOPLE_CARD_TEST_TAG)) {
        if (people.isEmpty()) {
            Text(stringResource(R.string.sky_people_empty), style = MaterialTheme.typography.bodyMedium)
            return@SkySection
        }
        Text(
            text = pluralStringResource(R.plurals.sky_people_count, people.size, people.size),
            style = MaterialTheme.typography.bodyLarge,
        )
        people.forEach { person -> PersonRow(person, now) }
    }
}

@Composable
private fun PersonRow(person: PersonInSpace, now: Instant) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NasaAsyncImage(
            url = person.imageUrl,
            contentDescription = null,
            fallbackIcon = Icons.Outlined.Person,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(person.name, style = MaterialTheme.typography.titleSmall)
            val details = listOfNotNull(person.agency, person.nationality).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            person.launchedAt?.let { launched ->
                val days = Duration.between(launched, now).toDays().coerceAtLeast(0).toInt()
                Text(
                    text = pluralStringResource(R.plurals.sky_people_days_up, days, days),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val PERCENT = 100
