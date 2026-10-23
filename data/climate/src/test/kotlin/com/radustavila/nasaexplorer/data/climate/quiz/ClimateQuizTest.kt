package com.radustavila.nasaexplorer.data.climate.quiz

import com.radustavila.nasaexplorer.core.model.ClimateIndicator
import com.radustavila.nasaexplorer.core.model.QuizArg
import com.radustavila.nasaexplorer.core.model.QuizPhrase
import com.radustavila.nasaexplorer.core.model.QuizText
import com.radustavila.nasaexplorer.core.testing.data.TestClimate
import com.radustavila.nasaexplorer.core.testing.fake.FakeClimateRepository
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimateQuizTest {

    private val now = Instant.parse("2026-10-23T09:00:00Z")

    @Test
    fun `no data, no questions or facts`() = runTest {
        val repository = FakeClimateRepository()

        assertEquals(emptyList<Any>(), ClimateQuizSource(repository).questions(Random(1), now))
        assertEquals(emptyList<Any>(), ClimateFunFactSource(repository).facts(now))
    }

    @Test
    fun `questions ask for the latest CO2 and temperature with clearly wrong distractors`() = runTest {
        val source = ClimateQuizSource(FakeClimateRepository(TestClimate.all()))

        val questions = source.questions(Random(7), now)

        assertEquals(listOf("climate-co2-2026-09-01", "climate-temperature-2026-09-01"), questions.map { it.id })
        val co2 = questions[0].answers.map { ((it as QuizText.Phrase).args.single() as QuizArg.Ppm).value }
        assertEquals(426.0, co2[questions[0].correctIndex], 0.0)
        assertEquals(4, co2.toSet().size)
        assertTrue(co2.filter { it != 426.0 }.all { kotlin.math.abs(it - 426.0) >= 30.0 })
        val temperature = questions[1].answers.map {
            ((it as QuizText.Phrase).args.single() as QuizArg.TemperatureChange).celsius
        }
        assertEquals(1.38, temperature[questions[1].correctIndex], 1e-9)
    }

    @Test
    fun `the same seed gives the same questions`() = runTest {
        val source = ClimateQuizSource(FakeClimateRepository(TestClimate.all()))

        assertEquals(source.questions(Random(3), now), source.questions(Random(3), now))
    }

    @Test
    fun `facts compare the latest values with 1980 and with the start of the sea level record`() = runTest {
        val facts = ClimateFunFactSource(FakeClimateRepository(TestClimate.all())).facts(now)

        assertEquals(listOf("live-climate-co2", "live-climate-sea-ice", "live-climate-sea-level"), facts.map { it.id })
        val co2 = facts[0].text as QuizText.Phrase
        assertEquals(QuizPhrase.FACT_CLIMATE_CO2, co2.phrase)
        assertEquals(QuizArg.Text("1980"), co2.args.last())
        val seaLevel = facts[2].text as QuizText.Phrase
        assertEquals(QuizArg.Text("1993"), seaLevel.args.last())
    }

    @Test
    fun `a sea ice record that grew tells no fact`() = runTest {
        val ice = TestClimate.arcticSeaIce().let {
            it.copy(points = it.points.dropLast(1) + it.points.last().copy(value = 9.0))
        }
        val facts = ClimateFunFactSource(FakeClimateRepository(listOf(ice))).facts(now)

        assertEquals(emptyList<Any>(), facts)
        assertEquals(ClimateIndicator.ARCTIC_SEA_ICE, ice.indicator)
    }
}
