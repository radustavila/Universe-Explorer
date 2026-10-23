package com.radustavila.nasaexplorer.data.climate.quiz

import com.radustavila.nasaexplorer.core.domain.quiz.FunFactSource
import com.radustavila.nasaexplorer.core.domain.quiz.QuizQuestionSource
import com.radustavila.nasaexplorer.core.domain.repository.ClimateRepository
import com.radustavila.nasaexplorer.core.model.ClimateIndicator
import com.radustavila.nasaexplorer.core.model.ClimateSeries
import com.radustavila.nasaexplorer.core.model.FunFact
import com.radustavila.nasaexplorer.core.model.QuizArg
import com.radustavila.nasaexplorer.core.model.QuizDifficulty
import com.radustavila.nasaexplorer.core.model.QuizPhrase
import com.radustavila.nasaexplorer.core.model.QuizQuestion
import com.radustavila.nasaexplorer.core.model.QuizText
import com.radustavila.nasaexplorer.core.model.QuizTopic
import java.time.Instant
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.flow.first

/**
 * Questions over the latest stored climate values (F26): today's CO₂ level at Mauna Loa and the
 * latest global temperature anomaly. Wrong answers sit a clear step away from the right one, so
 * rounding never decides.
 */
class ClimateQuizSource @Inject constructor(
    private val repository: ClimateRepository,
) : QuizQuestionSource {

    override val key: String = "climate"

    override val topics: Set<QuizTopic> = setOf(QuizTopic.EARTH)

    override suspend fun questions(random: Random, now: Instant): List<QuizQuestion> {
        val series = repository.observeSeries().first().associateBy { it.indicator }
        return listOfNotNull(
            series[ClimateIndicator.CO2]?.let { co2(it, random) },
            series[ClimateIndicator.GLOBAL_TEMPERATURE]?.let { temperature(it, random) },
        )
    }

    private fun co2(series: ClimateSeries, random: Random): QuizQuestion? {
        val latest = series.latest
        val change = series.change()
        if (latest == null || change == null) return null
        val right = latest.value.roundToInt().toDouble()
        val (answers, correct) = withDistractors(right, CO2_STEPS, random)
        return QuizQuestion(
            id = "climate-co2-${latest.date}",
            topic = QuizTopic.EARTH,
            difficulty = QuizDifficulty.MEDIUM,
            prompt = QuizText.Phrase(QuizPhrase.CLIMATE_CO2_QUESTION, QuizArg.Month(latest.date)),
            answers = answers.map { QuizText.Phrase(QuizPhrase.ANSWER_VALUE, QuizArg.Ppm(it)) },
            correctIndex = correct,
            explanation = QuizText.Phrase(
                QuizPhrase.CLIMATE_CO2_EXPLANATION,
                QuizArg.Ppm(latest.value),
                QuizArg.Month(latest.date),
                QuizArg.Ppm(change.delta),
                QuizArg.Text(change.sinceYear.toString()),
            ),
            sourceUrl = CO2_PAGE,
        )
    }

    private fun temperature(series: ClimateSeries, random: Random): QuizQuestion? {
        val latest = series.latest ?: return null
        val right = (latest.value * HUNDREDTHS).roundToInt() / HUNDREDTHS
        val (answers, correct) = withDistractors(right, TEMPERATURE_STEPS, random)
        return QuizQuestion(
            id = "climate-temperature-${latest.date}",
            topic = QuizTopic.EARTH,
            difficulty = QuizDifficulty.HARD,
            prompt = QuizText.Phrase(QuizPhrase.CLIMATE_TEMPERATURE_QUESTION, QuizArg.Month(latest.date)),
            answers = answers.map { QuizText.Phrase(QuizPhrase.ANSWER_VALUE, QuizArg.TemperatureChange(it)) },
            correctIndex = correct,
            explanation = QuizText.Phrase(
                QuizPhrase.CLIMATE_TEMPERATURE_EXPLANATION,
                QuizArg.TemperatureChange(latest.value),
                QuizArg.Month(latest.date),
            ),
            sourceUrl = GISTEMP_PAGE,
        )
    }

    companion object {
        /** Wrong CO₂ answers: three of these steps from the right value, in ppm. */
        private val CO2_STEPS = listOf(-90.0, -60.0, -30.0, 30.0)

        /** Wrong anomalies: three of these steps from the right value, in °C. */
        private val TEMPERATURE_STEPS = listOf(-0.8, -0.4, 0.4, 0.8)
        private const val HUNDREDTHS = 100.0
        private const val DISTRACTORS = 3

        const val CO2_PAGE = "https://gml.noaa.gov/ccgg/trends/"
        const val GISTEMP_PAGE = "https://data.giss.nasa.gov/gistemp/"
        const val SEA_LEVEL_PAGE = "https://www.star.nesdis.noaa.gov/socd/lsa/SeaLevelRise/"
        const val SEA_ICE_PAGE = "https://nsidc.org/data/g02135/versions/4"

        /** [right] and three values [steps] away, shuffled; the index of [right] among them. */
        internal fun withDistractors(right: Double, steps: List<Double>, random: Random): Pair<List<Double>, Int> {
            val answers = (steps.shuffled(random).take(DISTRACTORS).map { right + it } + right).shuffled(random)
            return answers to answers.indexOf(right)
        }
    }
}

/** "Did you know" lines from the latest climate values: CO₂, Arctic ice and the sea level against earlier years. */
class ClimateFunFactSource @Inject constructor(
    private val repository: ClimateRepository,
) : FunFactSource {

    override val key: String = "climate"

    override suspend fun facts(now: Instant): List<FunFact> {
        val series = repository.observeSeries().first().associateBy { it.indicator }
        return listOfNotNull(
            series[ClimateIndicator.CO2]?.let(::co2),
            series[ClimateIndicator.ARCTIC_SEA_ICE]?.let(::seaIce),
            series[ClimateIndicator.SEA_LEVEL]?.let(::seaLevel),
        )
    }

    private fun co2(series: ClimateSeries): FunFact? {
        val latest = series.latest
        val change = series.change()?.takeIf { it.delta > 0 }
        if (latest == null || change == null) return null
        return FunFact(
            id = "live-climate-co2",
            topic = QuizTopic.EARTH,
            text = QuizText.Phrase(
                QuizPhrase.FACT_CLIMATE_CO2,
                QuizArg.Ppm(latest.value),
                QuizArg.Month(latest.date),
                QuizArg.Ppm(change.delta),
                QuizArg.Text(change.sinceYear.toString()),
            ),
            sourceUrl = ClimateQuizSource.CO2_PAGE,
        )
    }

    private fun seaIce(series: ClimateSeries): FunFact? {
        val latest = series.latest
        val change = series.change()?.takeIf { it.delta < 0 }
        if (latest == null || change == null) return null
        return FunFact(
            id = "live-climate-sea-ice",
            topic = QuizTopic.EARTH,
            text = QuizText.Phrase(
                QuizPhrase.FACT_CLIMATE_SEA_ICE,
                QuizArg.MillionSquareKm(latest.value),
                QuizArg.Text(latest.date.year.toString()),
                QuizArg.MillionSquareKm(abs(change.delta)),
                QuizArg.Text(change.sinceYear.toString()),
            ),
            sourceUrl = ClimateQuizSource.SEA_ICE_PAGE,
        )
    }

    private fun seaLevel(series: ClimateSeries): FunFact? {
        val latest = series.latest
        val change = series.change()?.takeIf { it.delta > 0 }
        if (latest == null || change == null) return null
        return FunFact(
            id = "live-climate-sea-level",
            topic = QuizTopic.EARTH,
            text = QuizText.Phrase(
                QuizPhrase.FACT_CLIMATE_SEA_LEVEL,
                QuizArg.Millimetres(change.delta),
                QuizArg.Month(latest.date),
                QuizArg.Text(change.sinceYear.toString()),
            ),
            sourceUrl = ClimateQuizSource.SEA_LEVEL_PAGE,
        )
    }
}
