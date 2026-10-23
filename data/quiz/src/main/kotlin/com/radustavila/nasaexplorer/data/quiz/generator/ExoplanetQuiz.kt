package com.radustavila.nasaexplorer.data.quiz.generator

import com.radustavila.nasaexplorer.core.domain.quiz.FunFactSource
import com.radustavila.nasaexplorer.core.domain.quiz.QuizQuestionSource
import com.radustavila.nasaexplorer.core.domain.repository.ExoplanetRepository
import com.radustavila.nasaexplorer.core.model.Exoplanet
import com.radustavila.nasaexplorer.core.model.ExoplanetList
import com.radustavila.nasaexplorer.core.model.FunFact
import com.radustavila.nasaexplorer.core.model.QuizArg
import com.radustavila.nasaexplorer.core.model.QuizDifficulty
import com.radustavila.nasaexplorer.core.model.QuizPhrase
import com.radustavila.nasaexplorer.core.model.QuizQuestion
import com.radustavila.nasaexplorer.core.model.QuizText
import com.radustavila.nasaexplorer.core.model.QuizTopic
import java.net.URLEncoder
import java.time.Instant
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.first

/**
 * Higher or lower over the cached exoplanets (F8): which of three is nearest to us, which is the
 * widest. Three planets are only compared when they differ clearly, so rounding never decides.
 */
class ExoplanetQuizSource @Inject constructor(
    private val repository: ExoplanetRepository,
) : QuizQuestionSource {

    override val key: String = "exoplanets"

    override val topics: Set<QuizTopic> = setOf(QuizTopic.EXOPLANETS)

    override suspend fun questions(random: Random, now: Instant): List<QuizQuestion> {
        val planets = ExoplanetList.entries.flatMap { repository.observeList(it).first() }.distinctBy { it.name }
        return listOfNotNull(closer(planets, random), bigger(planets, random))
    }

    private fun closer(planets: List<Exoplanet>, random: Random): QuizQuestion? {
        val known = planets.filter { it.distanceLightYears != null }
        val trio = pickSpread(known, random) { it.distanceLightYears!! } ?: return null
        val nearest = trio.minBy { it.distanceLightYears!! }
        return QuizQuestion(
            id = "exoplanet-closer-" + trio.map { it.name }.sorted().joinToString("|"),
            topic = QuizTopic.EXOPLANETS,
            difficulty = QuizDifficulty.HARD,
            prompt = QuizText.Phrase(QuizPhrase.EXOPLANET_CLOSER_QUESTION),
            answers = trio.map { QuizText.Plain(it.name) },
            correctIndex = trio.indexOf(nearest),
            explanation = QuizText.Phrase(
                QuizPhrase.EXOPLANET_CLOSER_EXPLANATION,
                QuizArg.Text(nearest.name),
                QuizArg.LightYears(nearest.distanceLightYears!!),
            ),
            sourceUrl = archivePage(nearest.name),
        )
    }

    private fun bigger(planets: List<Exoplanet>, random: Random): QuizQuestion? {
        val trio = pickSpread(planets.filter { it.radiusEarth != null }, random) { it.radiusEarth!! } ?: return null
        val widest = trio.maxBy { it.radiusEarth!! }
        return QuizQuestion(
            id = "exoplanet-bigger-" + trio.map { it.name }.sorted().joinToString("|"),
            topic = QuizTopic.EXOPLANETS,
            difficulty = QuizDifficulty.MEDIUM,
            prompt = QuizText.Phrase(QuizPhrase.EXOPLANET_BIGGER_QUESTION),
            answers = trio.map { QuizText.Plain(it.name) },
            correctIndex = trio.indexOf(widest),
            explanation = QuizText.Phrase(
                QuizPhrase.EXOPLANET_BIGGER_EXPLANATION,
                QuizArg.Text(widest.name),
                QuizArg.EarthRadii(widest.radiusEarth!!),
            ),
            sourceUrl = archivePage(widest.name),
        )
    }

    companion object {
        /** The largest of the three must be at least this much bigger than the next one down. */
        const val MIN_RATIO = 1.3
        private const val ANSWERS = 3

        /**
         * [ANSWERS] items in a random order whose [value]s each differ by [MIN_RATIO] or more,
         * or null when the list has no such set.
         */
        internal fun <T> pickSpread(items: List<T>, random: Random, value: (T) -> Double): List<T>? {
            val picked = mutableListOf<T>()
            for (item in items.shuffled(random)) {
                val candidate = value(item)
                if (candidate <= 0.0) continue
                val clear = picked.all { other ->
                    val ratio = candidate / value(other)
                    ratio >= MIN_RATIO || ratio <= 1 / MIN_RATIO
                }
                if (clear) picked += item
                if (picked.size == ANSWERS) return picked
            }
            return null
        }

        internal fun archivePage(name: String): String =
            ARCHIVE_OVERVIEW + URLEncoder.encode(name, Charsets.UTF_8.name()).replace("+", "%20")

        private const val ARCHIVE_OVERVIEW = "https://exoplanetarchive.ipac.caltech.edu/overview/"
    }
}

/** "NASA's Exoplanet Archive lists 6,000 confirmed planets" from the cached catalogue figures. */
class ExoplanetFunFactSource @Inject constructor(
    private val repository: ExoplanetRepository,
) : FunFactSource {

    override val key: String = "exoplanets"

    override suspend fun facts(now: Instant): List<FunFact> {
        val stats = repository.observeCatalogStats().first()
        val confirmed = stats?.confirmedPlanets?.takeIf { it > 0 } ?: return emptyList()
        return listOf(
            FunFact(
                id = "live-confirmed-exoplanets",
                topic = QuizTopic.EXOPLANETS,
                text = QuizText.Phrase(QuizPhrase.FACT_CONFIRMED_EXOPLANETS, QuizArg.Count(confirmed)),
                sourceUrl = "https://exoplanetarchive.ipac.caltech.edu/",
            ),
        )
    }
}
