package com.radustavila.nasaexplorer.feature.quiz

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import com.radustavila.nasaexplorer.core.designsystem.skyevent.SkyEventText
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.model.DeepSkyType
import com.radustavila.nasaexplorer.core.model.QuizArg
import com.radustavila.nasaexplorer.core.model.QuizPhrase
import com.radustavila.nasaexplorer.core.model.QuizText
import java.text.NumberFormat

/**
 * Words a [QuizText]: the bank's text as it is, the app's own phrases from string resources with
 * their values formatted for the user (units, 12/24 h, plurals).
 */
class QuizTextFormatter(
    private val resources: Resources,
    private val units: UnitFormatter,
) {
    private val skyText = SkyEventText(resources)
    private val integers = NumberFormat.getIntegerInstance()
    private val percent = NumberFormat.getPercentInstance()

    fun format(text: QuizText): String = when (text) {
        is QuizText.Plain -> text.text
        is QuizText.Phrase -> phrase(text.phrase, text.args)
    }

    private fun phrase(phrase: QuizPhrase, args: List<QuizArg>): String {
        val values = args.map(::arg).toTypedArray()
        return when (val wording = wordingOf(phrase)) {
            is Wording.Single ->
                if (wording.id == NO_WORDS) values.joinToString(" ") else resources.getString(wording.id, *values)
            is Wording.Plural -> {
                val quantity = args.firstNotNullOfOrNull { (it as? QuizArg.Count)?.value } ?: 0
                // The first count picks the plural; every value, counts too, is already formatted text.
                resources.getQuantityString(wording.id, quantity, *values)
            }
        }
    }

    private fun arg(arg: QuizArg): String = when (arg) {
        is QuizArg.Text -> arg.value
        is QuizArg.Count -> integers.format(arg.value)
        is QuizArg.LargeDistanceKm -> units.largeDistance(arg.km)
        is QuizArg.LightYears -> units.number(arg.value, if (arg.value < SMALL_VALUE) 1 else 0)
        is QuizArg.EarthRadii -> units.number(arg.value, 1)
        is QuizArg.Magnitude -> units.number(arg.value, 1)
        is QuizArg.Percent -> percent.format(arg.fraction)
        is QuizArg.DayAndTime -> units.dayAndTime(arg.at)
        is QuizArg.Day -> units.day(arg.at)
        is QuizArg.PlanetName -> skyText.planet(arg.planet)
        is QuizArg.MoonPhaseName, is QuizArg.DeepSkyTypeName -> resources.getString(nameRes(arg))
        is QuizArg.Ppm, is QuizArg.TemperatureChange, is QuizArg.Millimetres, is QuizArg.MillionSquareKm,
        is QuizArg.Month,
        -> climateArg(arg)
    }

    /** The climate vital signs' values (F26). */
    private fun climateArg(arg: QuizArg): String = when (arg) {
        is QuizArg.Ppm -> resources.getString(R.string.quiz_value_ppm, units.number(arg.value, 0))
        is QuizArg.TemperatureChange -> units.temperatureChange(arg.celsius)
        is QuizArg.Millimetres -> units.smallLength(arg.mm)
        is QuizArg.MillionSquareKm -> units.millionArea(arg.value)
        is QuizArg.Month -> units.month(arg.date)
        else -> error("Not a climate value: $arg")
    }

    /** A Moon phase's or a kind of deep-sky object's name (F23). */
    @StringRes
    private fun nameRes(arg: QuizArg): Int = when (arg) {
        is QuizArg.MoonPhaseName -> moonPhaseName(arg.phase)
        is QuizArg.DeepSkyTypeName -> deepSkyTypeName(arg.type)
        else -> error("Not a name: $arg")
    }

    @StringRes
    private fun deepSkyTypeName(type: DeepSkyType): Int = when (type) {
        DeepSkyType.GALAXY -> R.string.quiz_deep_sky_galaxy
        DeepSkyType.OPEN_CLUSTER -> R.string.quiz_deep_sky_open_cluster
        DeepSkyType.GLOBULAR_CLUSTER -> R.string.quiz_deep_sky_globular_cluster
        DeepSkyType.PLANETARY_NEBULA -> R.string.quiz_deep_sky_planetary_nebula
        DeepSkyType.NEBULA -> R.string.quiz_deep_sky_nebula
        DeepSkyType.SUPERNOVA_REMNANT -> R.string.quiz_deep_sky_supernova_remnant
        DeepSkyType.DARK_NEBULA -> R.string.quiz_deep_sky_dark_nebula
        DeepSkyType.STAR_GROUP -> R.string.quiz_deep_sky_star_group
    }

    private sealed interface Wording {
        /** A plain string; id 0 means the values alone (an answer that is just a name or a number). */
        data class Single(@StringRes val id: Int) : Wording

        data class Plural(@PluralsRes val id: Int) : Wording
    }

    private fun wordingOf(phrase: QuizPhrase): Wording = WORDINGS.getValue(phrase)

    private companion object {
        /** Below this, a decimal still says something (4.2 light-years, not 4). */
        const val SMALL_VALUE = 10.0

        /** Not a resource: the answer is the values alone, a name or a number. */
        const val NO_WORDS = 0

        /** Every phrase's wording; `QuizScreensTest` checks none is missing. */
        val WORDINGS: Map<QuizPhrase, Wording> = mapOf(
            QuizPhrase.ANSWER_PLANET to Wording.Single(NO_WORDS),
            QuizPhrase.ANSWER_MOON_PHASE to Wording.Single(NO_WORDS),
            QuizPhrase.ANSWER_COUNT to Wording.Single(NO_WORDS),
            QuizPhrase.ANSWER_HUBBLE to Wording.Single(R.string.quiz_answer_hubble),
            QuizPhrase.ANSWER_WEBB to Wording.Single(R.string.quiz_answer_webb),
            QuizPhrase.EXOPLANET_CLOSER_QUESTION to Wording.Single(R.string.quiz_q_exoplanet_closer),
            QuizPhrase.EXOPLANET_CLOSER_EXPLANATION to Wording.Single(R.string.quiz_e_exoplanet_closer),
            QuizPhrase.EXOPLANET_BIGGER_QUESTION to Wording.Single(R.string.quiz_q_exoplanet_bigger),
            QuizPhrase.EXOPLANET_BIGGER_EXPLANATION to Wording.Single(R.string.quiz_e_exoplanet_bigger),
            QuizPhrase.HUBBLE_OR_WEBB_QUESTION to Wording.Single(R.string.quiz_q_hubble_or_webb),
            QuizPhrase.HUBBLE_OR_WEBB_EXPLANATION_HUBBLE to Wording.Single(R.string.quiz_e_hubble_or_webb_hubble),
            QuizPhrase.HUBBLE_OR_WEBB_EXPLANATION_WEBB to Wording.Single(R.string.quiz_e_hubble_or_webb_webb),
            QuizPhrase.PLANET_UP_TONIGHT_QUESTION to Wording.Single(R.string.quiz_q_planet_up),
            QuizPhrase.PLANET_UP_TONIGHT_EXPLANATION to Wording.Single(R.string.quiz_e_planet_up),
            QuizPhrase.MOON_PHASE_QUESTION to Wording.Single(R.string.quiz_q_moon_phase),
            QuizPhrase.MOON_PHASE_EXPLANATION to Wording.Single(R.string.quiz_e_moon_phase),
            QuizPhrase.PEOPLE_IN_SPACE_QUESTION to Wording.Single(R.string.quiz_q_people_in_space),
            QuizPhrase.PEOPLE_IN_SPACE_EXPLANATION to Wording.Plural(R.plurals.quiz_e_people_in_space),
            QuizPhrase.FARTHEST_SPACECRAFT_QUESTION to Wording.Single(R.string.quiz_q_farthest_spacecraft),
            QuizPhrase.FARTHEST_SPACECRAFT_EXPLANATION to Wording.Single(R.string.quiz_e_farthest_spacecraft),
            QuizPhrase.BRIGHTEST_COMET_QUESTION to Wording.Single(R.string.quiz_q_brightest_comet),
            QuizPhrase.BRIGHTEST_COMET_EXPLANATION to Wording.Single(R.string.quiz_e_brightest_comet),
            QuizPhrase.NEXT_LAUNCH_QUESTION to Wording.Single(R.string.quiz_q_next_launch),
            QuizPhrase.NEXT_LAUNCH_EXPLANATION to Wording.Single(R.string.quiz_e_next_launch),
            QuizPhrase.ANSWER_YES to Wording.Single(R.string.quiz_answer_yes),
            QuizPhrase.ANSWER_NO to Wording.Single(R.string.quiz_answer_no),
            QuizPhrase.BOOSTER_LANDED_QUESTION to Wording.Single(R.string.quiz_q_booster_landed),
            QuizPhrase.BOOSTER_LANDED_EXPLANATION_YES to Wording.Single(R.string.quiz_e_booster_landed_yes),
            QuizPhrase.BOOSTER_LANDED_EXPLANATION_NO to Wording.Single(R.string.quiz_e_booster_landed_no),
            QuizPhrase.ANSWER_VALUE to Wording.Single(NO_WORDS),
            QuizPhrase.CLIMATE_CO2_QUESTION to Wording.Single(R.string.quiz_q_climate_co2),
            QuizPhrase.CLIMATE_CO2_EXPLANATION to Wording.Single(R.string.quiz_e_climate_co2),
            QuizPhrase.CLIMATE_TEMPERATURE_QUESTION to Wording.Single(R.string.quiz_q_climate_temperature),
            QuizPhrase.CLIMATE_TEMPERATURE_EXPLANATION to Wording.Single(R.string.quiz_e_climate_temperature),
            QuizPhrase.FACT_CLIMATE_CO2 to Wording.Single(R.string.quiz_f_climate_co2),
            QuizPhrase.FACT_CLIMATE_SEA_ICE to Wording.Single(R.string.quiz_f_climate_sea_ice),
            QuizPhrase.FACT_CLIMATE_SEA_LEVEL to Wording.Single(R.string.quiz_f_climate_sea_level),
            QuizPhrase.ANSWER_DEEP_SKY_TYPE to Wording.Single(NO_WORDS),
            QuizPhrase.DEEP_SKY_TYPE_QUESTION to Wording.Single(R.string.quiz_q_deep_sky_type),
            QuizPhrase.DEEP_SKY_TYPE_EXPLANATION to Wording.Single(R.string.quiz_e_deep_sky),
            QuizPhrase.DEEP_SKY_CONSTELLATION_QUESTION to Wording.Single(R.string.quiz_q_deep_sky_constellation),
            QuizPhrase.DEEP_SKY_CONSTELLATION_EXPLANATION to Wording.Single(R.string.quiz_e_deep_sky),
            QuizPhrase.FACT_SPACECRAFT_DISTANCE to Wording.Single(R.string.quiz_f_spacecraft_distance),
            QuizPhrase.FACT_PEOPLE_IN_SPACE to Wording.Plural(R.plurals.quiz_f_people_in_space),
            QuizPhrase.FACT_CONFIRMED_EXOPLANETS to Wording.Plural(R.plurals.quiz_f_confirmed_exoplanets),
            QuizPhrase.FACT_NEXT_FULL_MOON to Wording.Single(R.string.quiz_f_next_full_moon),
        )
    }
}

@Composable
fun rememberQuizTextFormatter(): QuizTextFormatter {
    val resources = LocalResources.current
    val units = rememberUnitFormatter()
    return remember(resources, units) { QuizTextFormatter(resources, units) }
}
