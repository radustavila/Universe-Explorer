package com.radustavila.nasaexplorer.data.quiz.generator

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.location.ObserverLocationRepository
import com.radustavila.nasaexplorer.core.domain.quiz.FunFactSource
import com.radustavila.nasaexplorer.core.domain.quiz.QuizQuestionSource
import com.radustavila.nasaexplorer.core.domain.sky.PeopleInSpaceRepository
import com.radustavila.nasaexplorer.core.domain.sky.SkyCalculator
import com.radustavila.nasaexplorer.core.model.FunFact
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.MoonPhase
import com.radustavila.nasaexplorer.core.model.QuizArg
import com.radustavila.nasaexplorer.core.model.QuizDifficulty
import com.radustavila.nasaexplorer.core.model.QuizPhrase
import com.radustavila.nasaexplorer.core.model.QuizQuestion
import com.radustavila.nasaexplorer.core.model.QuizText
import com.radustavila.nasaexplorer.core.model.QuizTopic
import com.radustavila.nasaexplorer.core.model.SkyTonight
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Tonight's sky (F5), computed on the device: which planet is up from the saved place tonight, and
 * the Moon's phase (the same from everywhere, so it needs no place).
 */
class SkyTonightQuizSource @Inject constructor(
    private val locationRepository: ObserverLocationRepository,
    private val calculator: SkyCalculator,
    @Dispatcher(AppDispatcher.Default) private val computeDispatcher: CoroutineDispatcher,
) : QuizQuestionSource {

    override val key: String = "sky-tonight"

    override val topics: Set<QuizTopic> = setOf(QuizTopic.NIGHT_SKY)

    override suspend fun questions(random: Random, now: Instant): List<QuizQuestion> {
        val place = locationRepository.location.first()?.coordinate
        return withContext(computeDispatcher) {
            val sky = calculator.skyTonight(place ?: ANYWHERE, now)
            listOfNotNull(moonPhase(sky, random, now), place?.let { planetUp(sky, random, now) })
        }
    }

    private fun planetUp(sky: SkyTonight, random: Random, now: Instant): QuizQuestion? {
        val (visible, hidden) = sky.planets.partition { it.isVisibleTonight }
        if (visible.isEmpty() || hidden.isEmpty()) return null
        val answer = visible.random(random)
        val options = (hidden.shuffled(random).take(MAX_ANSWERS - 1) + answer).shuffled(random)
        return QuizQuestion(
            id = "planet-up-${now.dayKey()}-${answer.planet.name}",
            topic = QuizTopic.NIGHT_SKY,
            difficulty = QuizDifficulty.MEDIUM,
            prompt = QuizText.Phrase(QuizPhrase.PLANET_UP_TONIGHT_QUESTION),
            answers = options.map { QuizText.Phrase(QuizPhrase.ANSWER_PLANET, QuizArg.PlanetName(it.planet)) },
            correctIndex = options.indexOf(answer),
            explanation = QuizText.Phrase(
                QuizPhrase.PLANET_UP_TONIGHT_EXPLANATION,
                QuizArg.PlanetName(answer.planet),
                QuizArg.DayAndTime(checkNotNull(answer.bestTime)),
                QuizArg.Text(answer.constellation),
            ),
        )
    }

    private fun moonPhase(sky: SkyTonight, random: Random, now: Instant): QuizQuestion {
        val phase = sky.moon.phase
        val wrong = MoonPhase.entries.filter { it != phase }.shuffled(random).take(MAX_ANSWERS - 1)
        val options = (wrong + phase).shuffled(random)
        return QuizQuestion(
            id = "moon-phase-${now.dayKey()}",
            topic = QuizTopic.NIGHT_SKY,
            difficulty = QuizDifficulty.EASY,
            prompt = QuizText.Phrase(QuizPhrase.MOON_PHASE_QUESTION),
            answers = options.map { QuizText.Phrase(QuizPhrase.ANSWER_MOON_PHASE, QuizArg.MoonPhaseName(it)) },
            correctIndex = options.indexOf(phase),
            explanation = QuizText.Phrase(
                QuizPhrase.MOON_PHASE_EXPLANATION,
                QuizArg.Percent(sky.moon.illuminatedFraction),
                QuizArg.Day(sky.moon.nextFullMoon),
            ),
            sourceUrl = MOON_PHASES_PAGE,
        )
    }

    companion object {
        private const val MAX_ANSWERS = 4

        /** The Moon's phase is the same from everywhere; any place gives it. */
        internal val ANYWHERE = GeoCoordinate(latitude = 0.0, longitude = 0.0)

        internal const val MOON_PHASES_PAGE = "https://science.nasa.gov/moon/moon-phases/"
    }
}

/** "How many people are in space right now?" from the cached crew list (Launch Library 2, F5). */
class PeopleInSpaceQuizSource @Inject constructor(
    private val repository: PeopleInSpaceRepository,
) : QuizQuestionSource {

    override val key: String = "people-in-space"

    override val topics: Set<QuizTopic> = setOf(QuizTopic.HUMAN_SPACEFLIGHT)

    override suspend fun questions(random: Random, now: Instant): List<QuizQuestion> {
        val people = repository.observePeopleInSpace().first()
        val count = people.size
        if (count == 0) return emptyList()
        val wrong = OFFSETS.map { count + it }.filter { it > 0 }.shuffled(random).take(MAX_ANSWERS - 1)
        val options = (wrong + count).shuffled(random)
        return listOf(
            QuizQuestion(
                id = "people-in-space-$count",
                topic = QuizTopic.HUMAN_SPACEFLIGHT,
                difficulty = QuizDifficulty.MEDIUM,
                prompt = QuizText.Phrase(QuizPhrase.PEOPLE_IN_SPACE_QUESTION),
                answers = options.map { QuizText.Phrase(QuizPhrase.ANSWER_COUNT, QuizArg.Count(it)) },
                correctIndex = options.indexOf(count),
                explanation = QuizText.Phrase(
                    QuizPhrase.PEOPLE_IN_SPACE_EXPLANATION,
                    QuizArg.Count(count),
                    QuizArg.Text(people.random(random).name),
                ),
            ),
        )
    }

    private companion object {
        const val MAX_ANSWERS = 4
        val OFFSETS = listOf(-4, -3, -2, 2, 3, 4, 6)
    }
}

/** "10 people are in space right now." */
class PeopleInSpaceFunFactSource @Inject constructor(
    private val repository: PeopleInSpaceRepository,
) : FunFactSource {

    override val key: String = "people-in-space"

    override suspend fun facts(now: Instant): List<FunFact> {
        val count = repository.observePeopleInSpace().first().size
        if (count == 0) return emptyList()
        return listOf(
            FunFact(
                id = "live-people-in-space",
                topic = QuizTopic.HUMAN_SPACEFLIGHT,
                text = QuizText.Phrase(QuizPhrase.FACT_PEOPLE_IN_SPACE, QuizArg.Count(count)),
            ),
        )
    }
}

/** "The next full Moon is on Tue 4 Nov." */
class MoonFunFactSource @Inject constructor(
    private val calculator: SkyCalculator,
    @Dispatcher(AppDispatcher.Default) private val computeDispatcher: CoroutineDispatcher,
) : FunFactSource {

    override val key: String = "moon"

    override suspend fun facts(now: Instant): List<FunFact> {
        val sky = withContext(computeDispatcher) { calculator.skyTonight(SkyTonightQuizSource.ANYWHERE, now) }
        return listOf(
            FunFact(
                id = "live-next-full-moon",
                topic = QuizTopic.NIGHT_SKY,
                text = QuizText.Phrase(QuizPhrase.FACT_NEXT_FULL_MOON, QuizArg.Day(sky.moon.nextFullMoon)),
                sourceUrl = SkyTonightQuizSource.MOON_PHASES_PAGE,
            ),
        )
    }
}

/** The UTC day, enough to tell one night's question from the next. */
private fun Instant.dayKey(): String = atOffset(ZoneOffset.UTC).toLocalDate().toString()
