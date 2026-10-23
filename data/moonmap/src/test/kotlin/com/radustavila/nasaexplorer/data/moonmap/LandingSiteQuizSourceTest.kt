package com.radustavila.nasaexplorer.data.moonmap

import com.radustavila.nasaexplorer.core.model.QuizArg
import com.radustavila.nasaexplorer.core.model.QuizPhrase
import com.radustavila.nasaexplorer.core.model.QuizText
import com.radustavila.nasaexplorer.core.testing.data.TestMoonMap
import com.radustavila.nasaexplorer.core.testing.fake.FakeLunarLandingSiteRepository
import com.radustavila.nasaexplorer.data.moonmap.quiz.LandingSiteQuizSource
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LandingSiteQuizSourceTest {

    private val now = Instant.parse("2026-10-23T12:00:00Z")

    @Test
    fun `asks which mission landed at a site, with the right one among missions from elsewhere`() = runTest {
        val source = LandingSiteQuizSource(FakeLunarLandingSiteRepository())

        repeat(20) { seed ->
            val question = source.questions(Random(seed), now).single()
            val prompt = question.prompt as QuizText.Phrase
            assertEquals(QuizPhrase.LANDING_SITE_QUESTION, prompt.phrase)
            val region = (prompt.args[0] as QuizArg.Text).value
            val answers = question.answers.map { (it as QuizText.Plain).text }
            val right = TestMoonMap.sites.single { it.mission == answers[question.correctIndex] }
            assertEquals(region, right.region)
            assertEquals(right.landedOn.year.toString(), (prompt.args[1] as QuizArg.Text).value)
            val others = TestMoonMap.sites.filter { it.mission in answers && it != right }
            assertTrue(others.none { it.region == region })
            assertEquals("moon-landing-${right.id}", question.id)
        }
    }

    @Test
    fun `the same seed gives the same question`() = runTest {
        val source = LandingSiteQuizSource(FakeLunarLandingSiteRepository())

        assertEquals(source.questions(Random(7), now), source.questions(Random(7), now))
    }

    @Test
    fun `no sites, no questions`() = runTest {
        val source = LandingSiteQuizSource(FakeLunarLandingSiteRepository(sites = emptyList()))

        assertEquals(emptyList<Any>(), source.questions(Random(1), now))
    }
}
