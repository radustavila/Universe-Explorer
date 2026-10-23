package com.radustavila.nasaexplorer.data.quiz.generator

import com.radustavila.nasaexplorer.core.model.ExoplanetCatalogStats
import com.radustavila.nasaexplorer.core.model.GalleryRail
import com.radustavila.nasaexplorer.core.model.LaunchTimeline.PAST
import com.radustavila.nasaexplorer.core.model.MoonPhase
import com.radustavila.nasaexplorer.core.model.Planet
import com.radustavila.nasaexplorer.core.model.QuizArg
import com.radustavila.nasaexplorer.core.model.QuizPhrase
import com.radustavila.nasaexplorer.core.model.QuizQuestion
import com.radustavila.nasaexplorer.core.model.QuizText
import com.radustavila.nasaexplorer.core.testing.data.TestComets
import com.radustavila.nasaexplorer.core.testing.data.TestDeepSpaceNetwork
import com.radustavila.nasaexplorer.core.testing.data.TestExoplanets
import com.radustavila.nasaexplorer.core.testing.data.TestLaunches
import com.radustavila.nasaexplorer.core.testing.data.TestNasaMedia
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.fake.FakeCometRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeDeepSpaceNetworkRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeExoplanetRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeLaunchRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNasaMediaRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeObserverLocationRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakePeopleInSpaceRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSkyCalculator
import java.time.Duration
import java.time.Instant
import kotlin.random.Random
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizGeneratorsTest {

    private val now: Instant = Instant.parse("2026-10-22T12:00:00Z")

    private fun QuizQuestion.correctPlain(): String = (answers[correctIndex] as QuizText.Plain).text

    // Exoplanets (F8).

    @Test
    fun `exoplanets ask which of three clearly different planets is nearest and which is widest`() = runTest {
        val repository = FakeExoplanetRepository(
            initialPlanets = listOf(
                TestExoplanets.exoplanet(name = "Near b", distanceParsecs = 1.3, radiusEarth = 1.0),
                TestExoplanets.exoplanet(name = "Middle b", distanceParsecs = 12.0, radiusEarth = 2.5),
                TestExoplanets.exoplanet(name = "Far b", distanceParsecs = 300.0, radiusEarth = 11.0),
            ),
            newestSinceYear = 2000,
        )

        val questions = ExoplanetQuizSource(repository).questions(Random(1), now)

        assertEquals(2, questions.size)
        val closer = questions.single { (it.prompt as QuizText.Phrase).phrase == QuizPhrase.EXOPLANET_CLOSER_QUESTION }
        assertEquals("Near b", closer.correctPlain())
        val bigger = questions.single { (it.prompt as QuizText.Phrase).phrase == QuizPhrase.EXOPLANET_BIGGER_QUESTION }
        assertEquals("Far b", bigger.correctPlain())
        assertTrue(bigger.sourceUrl!!.endsWith("/overview/Far%20b"))
    }

    @Test
    fun `planets too alike to tell apart, or none cached, give no exoplanet question`() = runTest {
        val alike = FakeExoplanetRepository(
            initialPlanets = listOf("A b", "B b", "C b").map {
                TestExoplanets.exoplanet(name = it, distanceParsecs = 10.0, radiusEarth = 1.0)
            },
            newestSinceYear = 2000,
        )

        assertEquals(emptyList<QuizQuestion>(), ExoplanetQuizSource(alike).questions(Random(1), now))
        val empty = ExoplanetQuizSource(FakeExoplanetRepository())
        assertEquals(emptyList<QuizQuestion>(), empty.questions(Random(1), now))
    }

    @Test
    fun `the confirmed planet count becomes a fact once the catalogue is cached`() = runTest {
        val repository = FakeExoplanetRepository()
        val source = ExoplanetFunFactSource(repository)
        assertTrue(source.facts(now).isEmpty())

        repository.setStats(ExoplanetCatalogStats(confirmedPlanets = 6_128))

        val fact = source.facts(now).single().text as QuizText.Phrase
        assertEquals(QuizPhrase.FACT_CONFIRMED_EXOPLANETS, fact.phrase)
        assertEquals(listOf(QuizArg.Count(6_128)), fact.args)
    }

    // Universe gallery (F3).

    @Test
    fun `Hubble or Webb uses pictures that name exactly one telescope, one of each`() = runTest {
        val webb = TestNasaMedia.image(nasaId = "webb-1", title = "Webb sees a galaxy")
        val hubble = TestNasaMedia.image(nasaId = "hubble-1", title = "Hubble sees a nebula")
            .copy(keywords = listOf("Hubble"), description = "A nebula.")
        val both = TestNasaMedia.image(nasaId = "both", title = "Hubble and Webb compared")
        val video = TestNasaMedia.video()
        val repository = FakeNasaMediaRepository(
            mapOf(GalleryRail.WEBB to listOf(webb, both), GalleryRail.HUBBLE_GALAXIES to listOf(hubble, video)),
        )

        val questions = GalleryQuizSource(repository).questions(Random(3), now)

        assertEquals(setOf("hubble-or-webb-webb-1", "hubble-or-webb-hubble-1"), questions.map { it.id }.toSet())
        val webbQuestion = questions.single { it.id.endsWith("webb-1") }
        assertEquals(QuizText.Phrase(QuizPhrase.ANSWER_WEBB), webbQuestion.answers[webbQuestion.correctIndex])
        assertEquals(webb.previewUrl, webbQuestion.imageUrl)
        assertEquals(QuizPhrase.HUBBLE_OR_WEBB_EXPLANATION_WEBB, (webbQuestion.explanation as QuizText.Phrase).phrase)
    }

    @Test
    fun `an empty gallery gives no question`() = runTest {
        assertTrue(GalleryQuizSource(FakeNasaMediaRepository()).questions(Random(3), now).isEmpty())
    }

    // Sky tonight (F5).

    @Test
    fun `with a saved place the sky asks which planet is up tonight and the Moon's phase`() = runTest {
        val calculator = FakeSkyCalculator()
        val source = SkyTonightQuizSource(
            FakeObserverLocationRepository(TestSky.location()),
            calculator,
            StandardTestDispatcher(testScheduler),
        )

        val questions = source.questions(Random(4), now)

        assertEquals(2, questions.size)
        val planet = questions.single { (it.prompt as QuizText.Phrase).phrase == QuizPhrase.PLANET_UP_TONIGHT_QUESTION }
        val answer = (planet.answers[planet.correctIndex] as QuizText.Phrase).args.single() as QuizArg.PlanetName
        assertTrue(answer.planet in setOf(Planet.SATURN, Planet.JUPITER))
        // Mercury is the only planet down tonight: the one wrong answer.
        assertEquals(2, planet.answers.size)

        val moon = questions.single { (it.prompt as QuizText.Phrase).phrase == QuizPhrase.MOON_PHASE_QUESTION }
        val phase = (moon.answers[moon.correctIndex] as QuizText.Phrase).args.single()
        assertEquals(QuizArg.MoonPhaseName(MoonPhase.FIRST_QUARTER), phase)
        assertEquals(4, moon.answers.map { it }.distinct().size)
    }

    @Test
    fun `without a saved place only the Moon's phase is asked`() = runTest {
        val calculator = FakeSkyCalculator()
        val source = SkyTonightQuizSource(
            FakeObserverLocationRepository(),
            calculator,
            StandardTestDispatcher(testScheduler),
        )

        val questions = source.questions(Random(4), now)

        assertEquals(listOf(QuizPhrase.MOON_PHASE_QUESTION), questions.map { (it.prompt as QuizText.Phrase).phrase })
        assertEquals(SkyTonightQuizSource.ANYWHERE, calculator.requests.single().first)
    }

    @Test
    fun `people in space are counted, with wrong counts around the right one`() = runTest {
        val repository = FakePeopleInSpaceRepository()
        val source = PeopleInSpaceQuizSource(repository)
        assertTrue(source.questions(Random(5), now).isEmpty())

        repository.setPeople((1..10).map { TestSky.person(id = it, name = "Astronaut $it") })
        val question = source.questions(Random(5), now).single()

        assertEquals(QuizArg.Count(10), (question.answers[question.correctIndex] as QuizText.Phrase).args.single())
        assertEquals(4, question.answers.distinct().size)
        assertEquals(1, PeopleInSpaceFunFactSource(repository).facts(now).size)
    }

    // Deep Space Network (F13), comets (F18), launches (F2).

    @Test
    fun `the farthest spacecraft is the one Horizons puts farthest away now`() = runTest {
        val repository = FakeDeepSpaceNetworkRepository()
        val source = DeepSpaceQuizSource(repository)
        assertTrue(source.questions(Random(6), now).isEmpty())

        repository.farthest.value = TestDeepSpaceNetwork.farthest
        val question = source.questions(Random(6), now).single()

        assertEquals("Voyager 1", question.correctPlain())
        assertEquals(3, question.answers.size)
        val fact = DeepSpaceFunFactSource(repository).facts(now).single().text as QuizText.Phrase
        assertEquals(QuizArg.Text("Voyager 1"), fact.args.first())
    }

    @Test
    fun `the brightest comet this month wins, comets within half a magnitude of it are left out`() = runTest {
        val repository = FakeCometRepository(
            listOf(
                TestComets.comet(designation = "1", name = "C/2026 A1 (Bright)", peakMagnitude = 8.0, peakDay = 0),
                TestComets.comet(designation = "2", name = "C/2026 A2 (Close)", peakMagnitude = 8.3, peakDay = 0),
                TestComets.comet(designation = "3", name = "C/2026 A3 (Faint)", peakMagnitude = 14.0, peakDay = 0),
            ),
        )

        val question = CometQuizSource(repository).questions(Random(7), TestComets.NOW).single()

        assertEquals("C/2026 A1 (Bright)", question.correctPlain())
        val names = question.answers.map { (it as QuizText.Plain).text }.toSet()
        assertEquals(setOf("C/2026 A1 (Bright)", "C/2026 A3 (Faint)"), names)
    }

    @Test
    fun `comets with no brightness this month give no question`() = runTest {
        // Peaks far outside the ±15-day window still count by their in-window brightness, so
        // only a cache without comets or magnitudes gives nothing.
        assertTrue(CometQuizSource(FakeCometRepository()).questions(Random(7), TestComets.NOW).isEmpty())
        val single = FakeCometRepository(listOf(TestComets.comet()))
        assertTrue(CometQuizSource(single).questions(Random(7), TestComets.NOW).isEmpty())
    }

    @Test
    fun `the next launch's rocket is asked against other rockets that launch soon`() = runTest {
        val repository = FakeLaunchRepository()
        val source = LaunchQuizSource(repository)
        val launches = TestLaunches.upcoming(4).mapIndexed { index, launch -> launch.copy(rocket = "Rocket $index") }
        repository.setLaunches(launches)

        val question = source.questions(Random(8), TestLaunches.NOW).single()

        assertEquals("Rocket 0", question.correctPlain())
        assertEquals(4, question.answers.size)

        // Once it has flown, the next one is asked.
        val later = TestLaunches.NOW.plus(Duration.ofDays(1)).plusSeconds(60)
        assertEquals("Rocket 1", source.questions(Random(8), later).single().correctPlain())
    }

    @Test
    fun `launches all on the same rocket give no question`() = runTest {
        val repository = FakeLaunchRepository()
        repository.setLaunches(TestLaunches.upcoming(4))

        assertTrue(LaunchQuizSource(repository).questions(Random(8), TestLaunches.NOW).isEmpty())
    }

    @Test
    fun `a past launch's booster landing is asked as yes or no, answered as Launch Library 2 reports`() = runTest {
        val repository = FakeLaunchRepository()
        val source = BoosterLandingQuizSource(repository)
        val yesterday = TestLaunches.NOW.minus(Duration.ofDays(1))
        repository.setPastLaunches(
            listOf(
                TestLaunches.detailed(id = "lost", net = yesterday, timeline = PAST, landingSuccess = false),
                // Not reported yet: never asked.
                TestLaunches.detailed(id = "unknown", net = yesterday, timeline = PAST, landingSuccess = null),
            ),
        )

        val question = source.questions(Random(10), TestLaunches.NOW).single()

        val answers = question.answers.map { (it as QuizText.Phrase).phrase }
        assertEquals(listOf(QuizPhrase.ANSWER_YES, QuizPhrase.ANSWER_NO), answers)
        assertEquals(1, question.correctIndex)
        val explanation = question.explanation as QuizText.Phrase
        assertEquals(QuizPhrase.BOOSTER_LANDED_EXPLANATION_NO, explanation.phrase)
        assertEquals(listOf(QuizArg.Text("B1090"), QuizArg.Text("Of Course I Still Love You")), explanation.args)
    }

    @Test
    fun `no past launch with a reported landing gives no booster question`() = runTest {
        val repository = FakeLaunchRepository()
        repository.setPastLaunches(TestLaunches.past(3))

        assertTrue(BoosterLandingQuizSource(repository).questions(Random(10), TestLaunches.NOW).isEmpty())
    }

    @Test
    fun `the same random seed gives the same questions`() = runTest {
        val repository = FakeDeepSpaceNetworkRepository().apply { farthest.value = TestDeepSpaceNetwork.farthest }

        val source = DeepSpaceQuizSource(repository)
        assertEquals(source.questions(Random(9), now), source.questions(Random(9), now))
        val neither = TestNasaMedia.image(title = "A galaxy").copy(keywords = emptyList(), description = "")
        assertNull(GalleryQuizSource.telescopeOf(neither))
    }
}
