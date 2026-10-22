package com.radustavila.nasaexplorer.core.domain.sky

import com.radustavila.nasaexplorer.core.model.OrbitalElements
import com.radustavila.nasaexplorer.core.model.SatellitePass
import com.radustavila.nasaexplorer.core.model.SatelliteSet
import java.time.Duration
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** One object passes are predicted for: a named satellite, a Starlink train or a bright satellite. */
data class TrackedSatellite(
    val set: SatelliteSet,
    /** The elements the passes come from; for a train, those of the satellite in its middle. */
    val elements: OrbitalElements,
    /** Set for a Starlink train only. */
    val train: StarlinkTrain? = null,
) {
    val noradId: Int get() = elements.noradId

    /** CelesTrak's name, e.g. "SL-16 R/B"; the screen words the named sets itself. */
    val name: String get() = elements.name

    val typicalMagnitude: Double? get() = set.typicalMagnitude
}

/** The satellites of one Starlink launch, still close together in a line. */
data class StarlinkTrain(
    /** International designator of the launch, e.g. `2026-232`. */
    val launch: String,
    /** Estimated from the satellites' orbit counts: good to about a day. */
    val launchedAt: Instant,
    val satelliteCount: Int,
)

/** A visible pass of [satellite] over the observer. */
data class UpcomingPass(
    val satellite: TrackedSatellite,
    val pass: SatellitePass,
)

/** Why a set the user turned on has no passes in the list. */
enum class SatelliteSetStatus {
    /** Elements are in hand and were searched. */
    READY,

    /** Never fetched yet (first refresh pending or failed). */
    NO_DATA,

    /** The cached elements are too old for minute-accurate predictions. */
    STALE,

    /** Starlink only: no launch recent enough for its train to still be visible. */
    NO_RECENT_LAUNCH,
}

/** Visible passes of the satellites the user picked (all but the ISS), merged, soonest first. */
data class SatelliteForecast(
    val enabledSets: Set<SatelliteSet>,
    val passes: List<UpcomingPass>,
    /** One entry per enabled set. */
    val setStatus: Map<SatelliteSet, SatelliteSetStatus>,
    val computedAt: Instant,
)

/**
 * Starlink satellites leave the rocket in a tight line that spreads out and climbs over a few weeks;
 * only in the first days is it the eye-catching "train". This groups CelesTrak's recent Starlink
 * objects by launch and keeps the launches younger than [MAX_AGE].
 */
object StarlinkTrains {

    /** Past this the satellites are too spread out and too high (dimmer) to look like a train. */
    val MAX_AGE: Duration = Duration.ofDays(10)

    /** Each young launch with the elements of the satellite in the middle of its line. */
    fun from(elements: List<OrbitalElements>, now: Instant): List<TrackedSatellite> =
        elements
            .mapNotNull { member -> member.launchDesignator()?.let { launch -> launch to member } }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .mapNotNull { (launch, members) ->
                val launchedAt = estimateLaunch(members) ?: return@mapNotNull null
                if (Duration.between(launchedAt, now) > MAX_AGE) return@mapNotNull null
                TrackedSatellite(
                    set = SatelliteSet.STARLINK_TRAINS,
                    elements = middleOf(members),
                    train = StarlinkTrain(launch = launch, launchedAt = launchedAt, satelliteCount = members.size),
                )
            }
            .sortedByDescending { it.train?.launchedAt }

    /**
     * Epoch minus the orbits flown at it, per satellite, then the median: a few objects carry an
     * orbit count that is off by days. The low starting orbit makes the estimate a few hours late
     * at most.
     */
    private fun estimateLaunch(members: List<OrbitalElements>): Instant? {
        val estimates = members.mapNotNull { elements ->
            val revolutions = elements.revolutionsAtEpoch?.takeIf { it >= 0 } ?: return@mapNotNull null
            if (elements.meanMotion <= 0.0) return@mapNotNull null
            val flown = (revolutions / elements.meanMotion * SECONDS_PER_DAY).toLong()
            elements.epoch.minusSeconds(flown)
        }.sorted()
        return estimates.getOrNull(estimates.size / 2)
    }

    /**
     * The satellite closest to the circular mean of the line's along-track angles (argument of
     * pericentre plus mean anomaly), so a pass time is that of the train's middle.
     */
    private fun middleOf(members: List<OrbitalElements>): OrbitalElements {
        val angles = members.map { Math.toRadians(it.argOfPericenterDegrees + it.meanAnomalyDegrees) }
        val mean = atan2(angles.sumOf(::sin), angles.sumOf(::cos))
        return members.minBy { member ->
            val angle = Math.toRadians(member.argOfPericenterDegrees + member.meanAnomalyDegrees)
            1.0 - cos(angle - mean)
        }
    }

    /** `2026-232A` → `2026-232`. */
    private fun OrbitalElements.launchDesignator(): String? =
        internationalDesignator?.trim()?.let { LAUNCH_DESIGNATOR.find(it)?.value }

    private val LAUNCH_DESIGNATOR = Regex("""^\d{4}-\d{3}""")
    private const val SECONDS_PER_DAY = 86_400.0
}
