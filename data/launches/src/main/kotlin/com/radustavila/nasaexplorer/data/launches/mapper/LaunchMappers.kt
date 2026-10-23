package com.radustavila.nasaexplorer.data.launches.mapper

import com.radustavila.nasaexplorer.core.model.BoosterLanding
import com.radustavila.nasaexplorer.core.model.BoosterStage
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.Launch
import com.radustavila.nasaexplorer.core.model.LaunchMission
import com.radustavila.nasaexplorer.core.model.LaunchStatus
import com.radustavila.nasaexplorer.core.model.LaunchTimeline
import com.radustavila.nasaexplorer.core.model.NetPrecision
import com.radustavila.nasaexplorer.core.model.Webcast
import com.radustavila.nasaexplorer.data.launches.local.LaunchEntity
import com.radustavila.nasaexplorer.data.launches.remote.dto.AgencyDto
import com.radustavila.nasaexplorer.data.launches.remote.dto.LaunchDto
import com.radustavila.nasaexplorer.data.launches.remote.dto.LauncherStageDto
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * DTO to entity. Returns `null` for an entry the app cannot show (no id, name, page or a target
 * time that does not parse), so one bad entry never fails the whole refresh. Optional details that
 * are malformed are left out on their own.
 */
fun LaunchDto.toEntityOrNull(timeline: String = LaunchEntity.TIMELINE_UPCOMING): LaunchEntity? {
    val id = id?.takeIf(String::isNotBlank) ?: return null
    val name = name?.takeIf(String::isNotBlank) ?: return null
    val pageUrl = pageUrl() ?: return null
    val net = net?.toInstantOrNull() ?: return null
    val configuration = rocket?.configuration

    return LaunchEntity(
        id = id,
        name = name.trim(),
        netEpochMillis = net.toEpochMilli(),
        statusId = LaunchStatus.fromId(status?.id).id,
        rocket = configuration?.let { it.fullName ?: it.name }?.trim().orEmpty(),
        pad = listOfNotNull(pad?.name, pad?.location?.name)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(separator = ", "),
        provider = launchServiceProvider?.displayName(),
        imageUrl = (image?.thumbnailUrl ?: image?.imageUrl)?.takeIf(String::isNotBlank),
        // LL2 lists the streams in its own priority order; the first one is the official webcast.
        livestreamUrl = vidUrls.firstNotNullOfOrNull { video -> video.url?.takeIf(String::isNotBlank) },
        pageUrl = pageUrl,
        timeline = timeline,
        netPrecision = netPrecision?.id,
        windowStartMillis = windowStart?.toInstantOrNull()?.toEpochMilli(),
        windowEndMillis = windowEnd?.toInstantOrNull()?.toEpochMilli(),
        probability = probability?.takeIf { it in 0..PERCENT },
        failReason = failReason.cleaned(),
        missionName = mission?.name.cleaned(),
        missionDescription = mission?.description.cleaned(),
        missionType = mission?.type.cleaned(),
        orbitName = mission?.orbit?.name.cleaned(),
        orbitAbbrev = mission?.orbit?.abbrev.cleaned(),
        padLatitude = pad?.latitude?.takeIf { pad.longitude != null && it in -MAX_LATITUDE..MAX_LATITUDE },
        padLongitude = pad?.longitude?.takeIf { pad.latitude != null && it in -MAX_LONGITUDE..MAX_LONGITUDE },
        rocketName = configuration?.name.cleaned(),
        rocketVariant = configuration?.variant.cleaned(),
        boosters = json.encodeToString(rocket?.launcherStages.orEmpty().map { it.toStored() }),
        webcasts = json.encodeToString(
            vidUrls.mapNotNull { video ->
                video.url?.takeIf(String::isNotBlank)?.let {
                    StoredWebcast(
                        url = it,
                        title = video.title.cleaned(),
                        publisher = video.publisher.cleaned(),
                        live = video.live == true,
                    )
                }
            },
        ),
        crewCount = rocket?.spacecraftStages.orEmpty().sumOf { it.launchCrew.size },
    )
}

fun LaunchEntity.toDomain(): Launch = Launch(
    id = id,
    name = name,
    net = Instant.ofEpochMilli(netEpochMillis),
    status = LaunchStatus.fromId(statusId),
    rocket = rocket,
    pad = pad,
    provider = provider,
    imageUrl = imageUrl,
    livestreamUrl = livestreamUrl,
    pageUrl = pageUrl,
    netPrecision = NetPrecision.fromId(netPrecision),
    windowStart = windowStartMillis?.let(Instant::ofEpochMilli),
    windowEnd = windowEndMillis?.let(Instant::ofEpochMilli),
    probability = probability,
    mission = LaunchMission(
        name = missionName,
        description = missionDescription,
        type = missionType,
        orbitName = orbitName,
        orbitAbbrev = orbitAbbrev,
    ).takeIf { listOf(missionName, missionDescription, missionType, orbitName, orbitAbbrev).any { it != null } },
    padPosition = if (padLatitude != null && padLongitude != null) GeoCoordinate(padLatitude, padLongitude) else null,
    rocketName = rocketName,
    rocketVariant = rocketVariant,
    boosters = decodeList<StoredBooster>(boosters).map(StoredBooster::toDomain),
    webcasts = decodeList<StoredWebcast>(webcasts).map { Webcast(it.url, it.title, it.publisher, it.live) },
    crewCount = crewCount,
    failReason = failReason,
    timeline = if (timeline == LaunchEntity.TIMELINE_PAST) LaunchTimeline.PAST else LaunchTimeline.UPCOMING,
)

/** A booster as stored in [LaunchEntity.boosters]. Field names are persisted: never rename one. */
@Serializable
internal data class StoredBooster(
    val serial: String? = null,
    val flight: Int? = null,
    val type: String? = null,
    val landingAttempt: Boolean? = null,
    val landingSuccess: Boolean? = null,
    val landingLocation: String? = null,
    val landingLocationAbbrev: String? = null,
    val landingType: String? = null,
    val landingTypeAbbrev: String? = null,
    val hasLanding: Boolean = false,
)

/** A webcast as stored in [LaunchEntity.webcasts]. Field names are persisted: never rename one. */
@Serializable
internal data class StoredWebcast(
    val url: String,
    val title: String? = null,
    val publisher: String? = null,
    val live: Boolean = false,
)

private fun LauncherStageDto.toStored(): StoredBooster = StoredBooster(
    serial = launcher?.serialNumber.cleaned(),
    flight = launcherFlightNumber,
    type = type.cleaned(),
    landingAttempt = landing?.attempt,
    landingSuccess = landing?.success,
    landingLocation = landing?.landingLocation?.name.cleaned(),
    landingLocationAbbrev = landing?.landingLocation?.abbrev.cleaned(),
    landingType = landing?.type?.name.cleaned(),
    landingTypeAbbrev = landing?.type?.abbrev.cleaned(),
    hasLanding = landing != null,
)

private fun StoredBooster.toDomain(): BoosterStage = BoosterStage(
    serial = serial,
    flightNumber = flight,
    type = type,
    landing = if (hasLanding) {
        BoosterLanding(
            attempt = landingAttempt,
            success = landingSuccess,
            locationName = landingLocation,
            locationAbbrev = landingLocationAbbrev,
            typeName = landingType,
            typeAbbrev = landingTypeAbbrev,
        )
    } else {
        null
    },
)

private val json = Json { ignoreUnknownKeys = true }

/** A list stored by an older or broken write reads as empty rather than failing the screen. */
private inline fun <reified T> decodeList(text: String): List<T> = try {
    json.decodeFromString<List<T>>(text)
} catch (_: SerializationException) {
    emptyList()
} catch (_: IllegalArgumentException) {
    emptyList()
}

/** Launch Library 2 leaves empty strings and "Unknown" where it knows nothing. */
private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it != "Unknown" && it != "N/A" }

/**
 * The provider as a widget line can hold it: its name, or its abbreviation when the name is long
 * ("China Aerospace Science and Technology Corporation" becomes "CASC").
 */
private fun AgencyDto.displayName(): String? {
    val name = name?.trim()?.takeIf(String::isNotEmpty)
    val abbrev = abbrev?.trim()?.takeIf(String::isNotEmpty)
    return if (name != null && name.length > MAX_PROVIDER_NAME_LENGTH && abbrev != null) abbrev else name ?: abbrev
}

private const val MAX_PROVIDER_NAME_LENGTH = 24
private const val PERCENT = 100
private const val MAX_LATITUDE = 90.0
private const val MAX_LONGITUDE = 180.0

/**
 * A page for people: Space Launch Now, The Space Devs' own site on top of Launch Library 2. The API
 * URL is only a last resort - in a browser it shows the API's developer view, and every visit counts
 * against the same 15-calls-an-hour budget the app's refresh uses.
 */
private fun LaunchDto.pageUrl(): String? =
    slug?.takeIf(String::isNotBlank)?.let { "$SPACE_LAUNCH_NOW_LAUNCH_URL$it/" }
        ?: url?.takeIf(String::isNotBlank)

private const val SPACE_LAUNCH_NOW_LAUNCH_URL = "https://spacelaunchnow.app/launch/"

private fun String.toInstantOrNull(): Instant? = try {
    Instant.parse(this)
} catch (_: DateTimeParseException) {
    null
}
