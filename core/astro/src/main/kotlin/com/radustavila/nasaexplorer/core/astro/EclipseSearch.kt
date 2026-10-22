package com.radustavila.nasaexplorer.core.astro

import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.LocalLunarEclipse
import com.radustavila.nasaexplorer.core.model.LocalSolarEclipse
import com.radustavila.nasaexplorer.core.model.LunarEclipse
import com.radustavila.nasaexplorer.core.model.LunarEclipseType
import com.radustavila.nasaexplorer.core.model.SolarEclipse
import com.radustavila.nasaexplorer.core.model.SolarEclipseType
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.EclipseKind
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.searchGlobalSolarEclipse
import io.github.cosinekitty.astronomy.searchLocalSolarEclipse
import io.github.cosinekitty.astronomy.searchLunarEclipse
import java.time.Instant
import kotlin.math.abs

/** Lunar and solar eclipses, with what the observer sees of them. */
internal object EclipseSearch {

    private const val LOCAL_ECLIPSE_WINDOW_DAYS = 1.0
    private const val MILLIS_PER_MINUTE = 60_000.0

    fun lunarEclipses(range: ClosedRange<Instant>, site: Observer?): List<LunarEclipse> =
        generateSequence(searchLunarEclipse(range.start.toAstronomyTime())) { previous ->
            searchLunarEclipse(previous.peak.addDays(SEARCH_STEP_DAYS))
        }.takeWhile { it.peak.toInstant() < range.endInclusive }.map { info ->
            val peak = info.peak.toInstant()
            val penumbral = checkNotNull(peak.around(info.sdPenum))
            val partial = peak.around(info.sdPartial)
            val total = peak.around(info.sdTotal)
            val mainPhase = partial ?: penumbral
            LunarEclipse(
                time = peak,
                type = when (info.kind) {
                    EclipseKind.Total -> LunarEclipseType.TOTAL
                    EclipseKind.Partial -> LunarEclipseType.PARTIAL
                    else -> LunarEclipseType.PENUMBRAL
                },
                obscuration = info.obscuration,
                penumbralStart = penumbral.first,
                penumbralEnd = penumbral.second,
                partialStart = partial?.first,
                partialEnd = partial?.second,
                totalStart = total?.first,
                totalEnd = total?.second,
                local = site?.let {
                    LocalLunarEclipse(
                        moonAltitudeAtPeak = altitude(Body.Moon, it, peak),
                        isVisible = isAboveHorizonDuring(Body.Moon, it, mainPhase.first, mainPhase.second),
                    )
                },
            )
        }.toList()

    fun solarEclipses(range: ClosedRange<Instant>, site: Observer?): List<SolarEclipse> =
        generateSequence(searchGlobalSolarEclipse(range.start.toAstronomyTime())) { previous ->
            searchGlobalSolarEclipse(previous.peak.addDays(SEARCH_STEP_DAYS))
        }.takeWhile { it.peak.toInstant() < range.endInclusive }.map { info ->
            val type = info.kind.toSolarEclipseType()
            // A partial eclipse's shadow axis misses the Earth: there is no such point.
            val greatest = GeoCoordinate(info.latitude, info.longitude).takeUnless { type == SolarEclipseType.PARTIAL }
            SolarEclipse(
                time = info.peak.toInstant(),
                type = type,
                greatestEclipseAt = greatest,
                local = site?.let { localSolarEclipse(it, info.peak) },
            )
        }.toList()

    /**
     * The eclipse peaking at [globalPeak] as seen from [site], or null when none of it is above the
     * horizon there. Astronomy Engine's local search skips eclipses that happen entirely at night
     * and only returns those the observer is inside the Moon's penumbra for, so a result for a
     * later new Moon means this one cannot be seen.
     */
    private fun localSolarEclipse(site: Observer, globalPeak: Time): LocalSolarEclipse? {
        val local = searchLocalSolarEclipse(globalPeak.addDays(-LOCAL_ECLIPSE_WINDOW_DAYS), site)
        if (abs(local.peak.time.ut - globalPeak.ut) > LOCAL_ECLIPSE_WINDOW_DAYS) return null
        return LocalSolarEclipse(
            type = local.kind.toSolarEclipseType(),
            obscuration = local.obscuration,
            partialStart = local.partialBegin.time.toInstant(),
            totalStart = local.totalBegin?.time?.toInstant(),
            peak = local.peak.time.toInstant(),
            totalEnd = local.totalEnd?.time?.toInstant(),
            partialEnd = local.partialEnd.time.toInstant(),
            sunAltitudeAtPeak = local.peak.altitude,
        )
    }

    /** The phase lasting [semiDurationMinutes] either side of this peak, or null when there is none. */
    private fun Instant.around(semiDurationMinutes: Double): Pair<Instant, Instant>? {
        if (semiDurationMinutes <= 0.0) return null
        val half = (semiDurationMinutes * MILLIS_PER_MINUTE).toLong()
        return minusMillis(half) to plusMillis(half)
    }

    private fun EclipseKind.toSolarEclipseType(): SolarEclipseType = when (this) {
        EclipseKind.Total -> SolarEclipseType.TOTAL
        EclipseKind.Annular -> SolarEclipseType.ANNULAR
        else -> SolarEclipseType.PARTIAL
    }
}
