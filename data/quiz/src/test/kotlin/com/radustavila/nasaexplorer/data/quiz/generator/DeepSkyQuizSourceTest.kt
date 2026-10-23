package com.radustavila.nasaexplorer.data.quiz.generator

import com.radustavila.nasaexplorer.core.model.ConstellationFigure
import com.radustavila.nasaexplorer.core.model.EquatorialCoordinate
import com.radustavila.nasaexplorer.core.model.QuizArg
import com.radustavila.nasaexplorer.core.model.QuizPhrase
import com.radustavila.nasaexplorer.core.model.QuizText
import com.radustavila.nasaexplorer.core.testing.data.TestDeepSky
import com.radustavila.nasaexplorer.core.testing.data.TestSkyMap
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyCatalogRepository
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSkyQuizSourceTest {

    private val now: Instant = Instant.parse("2026-10-22T12:00:00Z")

    private fun figure(id: String, name: String) =
        ConstellationFigure(id, name, EquatorialCoordinate(0.0, 0.0), rank = 1, lines = emptyList())

    private val catalog = TestSkyMap.catalog().copy(
        constellations = listOf(
            figure("And", "Andromeda"),
            figure("Ori", "Orion"),
            figure("Her", "Hercules"),
            figure("Per", "Perseus"),
            figure("Cyg", "Cygnus"),
        ),
        deepSky = TestDeepSky.objects(),
    )

    @Test
    fun `asks what kind of object and which constellation, about named Messier objects`() = runTest {
        val source = DeepSkyQuizSource(FakeSkyCatalogRepository(catalog), StandardTestDispatcher(testScheduler))

        val questions = source.questions(Random(3), now)

        assertEquals(2, questions.size)
        val messier = TestDeepSky.objects().filter { it.messier != null }.associateBy { "M${it.messier}" }
        val type = questions.single { (it.prompt as QuizText.Phrase).phrase == QuizPhrase.DEEP_SKY_TYPE_QUESTION }
        val typeOf = messier.getValue(((type.prompt as QuizText.Phrase).args.single() as QuizArg.Text).value)
        val answer = type.answers[type.correctIndex] as QuizText.Phrase
        assertEquals(QuizArg.DeepSkyTypeName(typeOf.type), answer.args.single())
        assertEquals(4, type.answers.toSet().size)
        assertEquals(typeOf.nasaPageUrl, type.sourceUrl)

        val where = questions.single {
            (it.prompt as QuizText.Phrase).phrase == QuizPhrase.DEEP_SKY_CONSTELLATION_QUESTION
        }
        val whereOf = messier.getValue(((where.prompt as QuizText.Phrase).args.single() as QuizArg.Text).value)
        val names = catalog.constellations.associate { it.id to it.name }
        assertEquals(names.getValue(whereOf.constellation), (where.answers[where.correctIndex] as QuizText.Plain).text)
        assertTrue(where.answers.size >= 2)
        // Caldwell-only objects (no Messier number) are never asked about.
        assertTrue(typeOf.messier != null && whereOf.messier != null && typeOf != whereOf)
    }

    @Test
    fun `the same seed gives the same questions`() = runTest {
        val source = DeepSkyQuizSource(FakeSkyCatalogRepository(catalog), StandardTestDispatcher(testScheduler))

        assertEquals(source.questions(Random(7), now), source.questions(Random(7), now))
    }

    @Test
    fun `without the deep-sky catalogue there is nothing to ask`() = runTest {
        val source = DeepSkyQuizSource(FakeSkyCatalogRepository(), StandardTestDispatcher(testScheduler))

        assertEquals(emptyList<Any>(), source.questions(Random(1), now))
    }
}
