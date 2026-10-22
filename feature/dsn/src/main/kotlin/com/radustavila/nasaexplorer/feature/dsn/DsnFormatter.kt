package com.radustavila.nasaexplorer.feature.dsn

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import java.time.Duration

/**
 * Wording for the network's numbers. Light time and data rates are the same in every measurement
 * system, so they are worded here rather than in [UnitFormatter]; their digits still follow the
 * user's locale through [UnitFormatter.number].
 */
internal class DsnFormatter(private val resources: Resources, private val units: UnitFormatter) {

    /** "4.9 s", "8 min 20 s", "19 h 58 min". */
    fun lightTime(duration: Duration): String {
        val seconds = duration.toMillis() / MILLIS_PER_SECOND
        val whole = duration.seconds
        return when {
            seconds < SECONDS_PER_MINUTE -> resources.getString(R.string.dsn_duration_seconds, units.number(seconds, 1))
            whole < SECONDS_PER_HOUR -> resources.getString(
                R.string.dsn_duration_minutes_seconds,
                (whole / SECONDS_PER_MINUTE).toInt(),
                (whole % SECONDS_PER_MINUTE).toInt(),
            )
            else -> resources.getString(
                R.string.dsn_duration_hours_minutes,
                (whole / SECONDS_PER_HOUR).toInt(),
                (whole % SECONDS_PER_HOUR / SECONDS_PER_MINUTE).toInt(),
            )
        }
    }

    /** "160 bit/s", "52 kbit/s", "2.5 Mbit/s". */
    fun dataRate(bitsPerSecond: Double): String = when {
        bitsPerSecond < KILO -> resources.getString(R.string.dsn_rate_bps, units.number(bitsPerSecond, 0))
        bitsPerSecond < MEGA -> resources.getString(R.string.dsn_rate_kbps, scaled(bitsPerSecond / KILO))
        else -> resources.getString(R.string.dsn_rate_mbps, scaled(bitsPerSecond / MEGA))
    }

    /** "172.65 AU": astronomical units mean the same in every system, so they are never converted. */
    fun astronomicalUnits(au: Double): String = resources.getString(R.string.dsn_farthest_au, units.number(au, 2))

    /** Kilometres, or miles for imperial users: "21.6 billion km". */
    fun distance(km: Double): String = units.largeDistance(km)

    private fun scaled(value: Double): String = units.number(value, if (value < TEN) 1 else 0)

    private companion object {
        const val MILLIS_PER_SECOND = 1_000.0
        const val SECONDS_PER_MINUTE = 60L
        const val SECONDS_PER_HOUR = 3_600L
        const val KILO = 1_000.0
        const val MEGA = 1_000_000.0
        const val TEN = 10.0
    }
}

@Composable
internal fun rememberDsnFormatter(): DsnFormatter = DsnFormatter(LocalResources.current, rememberUnitFormatter())

/** "DSS43" → "DSS 43", as NASA writes the antennas' names. */
@Composable
internal fun dishLabel(name: String): String {
    val number = name.removePrefix("DSS").takeIf { it != name } ?: return name
    return LocalResources.current.getString(R.string.dsn_dish_name, number)
}

/** A one-line note about the mission, for the spacecraft the network talks to most; null for the rest. */
@StringRes
internal fun missionNoteRes(code: String): Int? = MISSION_NOTES[code]

private val MISSION_NOTES: Map<String, Int> = mapOf(
    "VGR1" to R.string.dsn_note_vgr1,
    "VGR2" to R.string.dsn_note_vgr2,
    "NHPC" to R.string.dsn_note_nhpc,
    "JWST" to R.string.dsn_note_jwst,
    "MRO" to R.string.dsn_note_mro,
    "M01O" to R.string.dsn_note_m01o,
    "MVN" to R.string.dsn_note_mvn,
    "MSL" to R.string.dsn_note_msl,
    "M20" to R.string.dsn_note_m20,
    "JNO" to R.string.dsn_note_jno,
    "EURC" to R.string.dsn_note_eurc,
    "SPP" to R.string.dsn_note_spp,
    "STA" to R.string.dsn_note_sta,
    "ACE" to R.string.dsn_note_ace,
    "SOHO" to R.string.dsn_note_soho,
    "WIND" to R.string.dsn_note_wind,
    "ORX" to R.string.dsn_note_orx,
    "RST" to R.string.dsn_note_rst,
    "MMX" to R.string.dsn_note_mmx,
    "LRO" to R.string.dsn_note_lro,
    "PSYC" to R.string.dsn_note_psyc,
    "LUCY" to R.string.dsn_note_lucy,
    "BEPI" to R.string.dsn_note_bepi,
    "TGO" to R.string.dsn_note_tgo,
    "MEX" to R.string.dsn_note_mex,
    "EMM" to R.string.dsn_note_emm,
    "CHDR" to R.string.dsn_note_chdr,
    "TESS" to R.string.dsn_note_tess,
    "KPLO" to R.string.dsn_note_kplo,
    "GAIA" to R.string.dsn_note_gaia,
)
