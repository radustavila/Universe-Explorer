package com.radustavila.nasaexplorer.data.dsn.mapper

import com.radustavila.nasaexplorer.core.model.DeepSpaceProbe
import com.radustavila.nasaexplorer.core.model.DsnDish
import com.radustavila.nasaexplorer.core.model.DsnLink
import com.radustavila.nasaexplorer.core.model.DsnSignal
import com.radustavila.nasaexplorer.core.model.DsnSite
import com.radustavila.nasaexplorer.core.model.DsnSnapshot
import com.radustavila.nasaexplorer.core.model.DsnSpacecraft
import com.radustavila.nasaexplorer.data.dsn.local.DsnDishEntity
import com.radustavila.nasaexplorer.data.dsn.local.DsnDishInfoEntity
import com.radustavila.nasaexplorer.data.dsn.local.DsnDishWithLinks
import com.radustavila.nasaexplorer.data.dsn.local.DsnLinkEntity
import com.radustavila.nasaexplorer.data.dsn.local.DsnSignalEntity
import com.radustavila.nasaexplorer.data.dsn.local.DsnSpacecraftEntity
import com.radustavila.nasaexplorer.data.dsn.local.ProbeDistanceEntity
import com.radustavila.nasaexplorer.data.dsn.remote.dto.DsnConfigDto
import com.radustavila.nasaexplorer.data.dsn.remote.dto.DsnDishDto
import com.radustavila.nasaexplorer.data.dsn.remote.dto.DsnLiveDto
import com.radustavila.nasaexplorer.data.dsn.remote.dto.DsnSignalDto
import com.radustavila.nasaexplorer.data.dsn.remote.dto.HorizonsRangeDto
import java.time.Instant
import java.util.Locale

/** The rows one live refresh stores. */
internal data class DsnLiveRows(
    val dishes: List<DsnDishEntity>,
    val links: List<DsnLinkEntity>,
    val signals: List<DsnSignalEntity>,
)

/** The rows one `config.xml` refresh stores. */
internal data class DsnConfigRows(
    val spacecraft: List<DsnSpacecraftEntity>,
    val dishes: List<DsnDishInfoEntity>,
)

/**
 * The feed as rows, or null when it lists no dish at all (a changed or broken feed, which must not
 * replace the last good state). Idle dishes stay, without links: the feed points them at a
 * placeholder target such as `DSN` (id 99) while they are under maintenance or unassigned.
 */
internal fun DsnLiveDto.toRowsOrNull(fetchedAtMillis: Long): DsnLiveRows? {
    val observedAt = timestampMillis ?: stations.firstNotNullOfOrNull { it.timeUtcMillis } ?: fetchedAtMillis
    val dishes = mutableListOf<DsnDishEntity>()
    val links = mutableListOf<DsnLinkEntity>()
    val signals = mutableListOf<DsnSignalEntity>()
    stations.forEachIndexed { siteOrder, station ->
        // A dish the feed lists twice keeps its first entry: its name is the key.
        station.dishes.distinctBy { it.name }.filter { dish -> dishes.none { it.dishName == dish.name } }
            .forEachIndexed { dishOrder, dish ->
                dishes += DsnDishEntity(
                    dishName = dish.name,
                    siteCode = station.name,
                    siteName = station.friendlyName,
                    siteOrder = siteOrder,
                    dishOrder = dishOrder,
                    azimuthDegrees = dish.azimuth,
                    elevationDegrees = dish.elevation,
                    activity = dish.activity,
                    observedAtMillis = observedAt,
                    fetchedAtMillis = fetchedAtMillis,
                )
                links += dish.toLinks()
                signals += dish.toSignals()
            }
    }
    return if (dishes.isEmpty()) null else DsnLiveRows(dishes, links, signals)
}

private fun DsnDishDto.toLinks(): List<DsnLinkEntity> {
    val placeholders = targets.filter { it.id == PLACEHOLDER_TARGET_ID }.map { it.name.code() }.toSet()
    val codes = (targets.map { it.name } + (upSignals + downSignals).mapNotNull(DsnSignalDto::spacecraft))
        .map { it.code() }
        .distinct()
        .filterNot { it in placeholders || it in PLACEHOLDER_CODES }
    return codes.mapIndexed { order, code ->
        val target = targets.firstOrNull { it.name.code() == code }
        val signalId = (upSignals + downSignals).firstOrNull { it.spacecraft?.code() == code }?.spacecraftId
        DsnLinkEntity(
            dishName = name,
            spacecraftCode = code,
            linkOrder = order,
            // Signals carry the NAIF id (-32); targets carry it unsigned (32).
            naifId = signalId ?: target?.id?.let { -it },
            rangeKm = target?.let { it.downlegRange.positiveOrNull() ?: it.uplegRange.positiveOrNull() },
        )
    }
}

private fun DsnDishDto.toSignals(): List<DsnSignalEntity> {
    val onlyTarget = targets.singleOrNull()?.name?.code()
    fun DsnSignalDto.toEntity(isUplink: Boolean): DsnSignalEntity? =
        (spacecraft?.code() ?: onlyTarget)?.takeUnless { it in PLACEHOLDER_CODES }?.let { code ->
            DsnSignalEntity(
                dishName = name,
                spacecraftCode = code,
                isUplink = isUplink,
                band = band,
                dataRateBitsPerSecond = dataRate?.takeIf { it >= 0 },
                isActive = active,
            )
        }
    return upSignals.mapNotNull { it.toEntity(isUplink = true) } +
        downSignals.mapNotNull { it.toEntity(isUplink = false) }
}

internal fun DsnConfigDto.toRows(fetchedAtMillis: Long) = DsnConfigRows(
    spacecraft = spacecraft.mapNotNull { dto ->
        dto.friendlyName?.let { name ->
            DsnSpacecraftEntity(code = dto.name.code(), name = name, fetchedAtMillis = fetchedAtMillis)
        }
    }.distinctBy { it.code },
    dishes = sites.flatMap { site ->
        site.dishes.map { dish ->
            DsnDishInfoEntity(
                dishName = dish.name,
                siteCode = site.name,
                diameterMeters = dish.type?.let { DIAMETER.find(it)?.value?.toIntOrNull() },
                fetchedAtMillis = fetchedAtMillis,
            )
        }
    }.distinctBy { it.dishName },
)

internal fun HorizonsRangeDto.toEntity(fetchedAtMillis: Long) = ProbeDistanceEntity(
    horizonsId = horizonsId,
    name = name,
    distanceAu = deltaAu,
    rangeRateKmPerSecond = rangeRateKmPerSecond,
    epochMillis = epochMillis,
    fetchedAtMillis = fetchedAtMillis,
)

internal fun ProbeDistanceEntity.toDomain() = DeepSpaceProbe(
    horizonsId = horizonsId,
    name = name,
    distanceAu = distanceAu,
    rangeRateKmPerSecond = rangeRateKmPerSecond,
    at = Instant.ofEpochMilli(epochMillis),
)

/**
 * The stored snapshot as the domain sees it, or null when nothing is stored. Spacecraft without a
 * range in the feed get today's Horizons distance when one of [probes] is that spacecraft.
 */
internal fun List<DsnDishWithLinks>.toSnapshotOrNull(
    names: Map<String, String>,
    diameters: Map<String, Int?>,
    probes: Map<Int, ProbeDistanceEntity>,
): DsnSnapshot? {
    val observedAt = Instant.ofEpochMilli(firstOrNull()?.dish?.observedAtMillis ?: return null)
    val sites = groupBy { it.dish.siteOrder }.toSortedMap().values.map { dishes ->
        val site = dishes.first().dish
        DsnSite(
            code = site.siteCode,
            name = site.siteName ?: site.siteCode,
            dishes = dishes.sortedBy { it.dish.dishOrder }.map { it.toDomain(names, diameters, probes, observedAt) },
        )
    }
    return DsnSnapshot(observedAt = observedAt, sites = sites)
}

private fun DsnDishWithLinks.toDomain(
    names: Map<String, String>,
    diameters: Map<String, Int?>,
    probes: Map<Int, ProbeDistanceEntity>,
    observedAt: Instant,
) = DsnDish(
    name = dish.dishName,
    diameterMeters = diameters[dish.dishName],
    azimuthDegrees = dish.azimuthDegrees,
    elevationDegrees = dish.elevationDegrees,
    activity = dish.activity,
    links = links.sortedBy(DsnLinkEntity::linkOrder).map { link ->
        val ownSignals = signals.filter { it.spacecraftCode == link.spacecraftCode }
        val horizons = link.naifId?.let(probes::get)?.toDomain()?.distanceKmAt(observedAt)
        DsnLink(
            spacecraft = DsnSpacecraft(
                code = link.spacecraftCode,
                name = names[link.spacecraftCode],
                naifId = link.naifId,
            ),
            upSignals = ownSignals.filter(DsnSignalEntity::isUplink).map(DsnSignalEntity::toDomain),
            downSignals = ownSignals.filterNot(DsnSignalEntity::isUplink).map(DsnSignalEntity::toDomain),
            distanceKm = link.rangeKm ?: horizons,
            distanceSource = when {
                link.rangeKm != null -> DsnLink.DistanceSource.DSN
                horizons != null -> DsnLink.DistanceSource.HORIZONS
                else -> null
            },
        )
    },
)

private fun DsnSignalEntity.toDomain() =
    DsnSignal(band = band, dataRateBitsPerSecond = dataRateBitsPerSecond, isActive = isActive)

private fun String.code(): String = trim().uppercase(Locale.ROOT)

private fun Double?.positiveOrNull(): Double? = this?.takeIf { it > 0 }

/** The feed's target id for "no spacecraft" (maintenance, tests, idle). */
private const val PLACEHOLDER_TARGET_ID = 99

/** Codes the feed uses for the network itself rather than a spacecraft. */
private val PLACEHOLDER_CODES = setOf("DSN", "DSS")

private val DIAMETER = Regex("^\\d+")
