package com.radustavila.nasaexplorer.data.moonmap.quiz

import com.radustavila.nasaexplorer.core.domain.moon.LunarLandingSiteRepository
import com.radustavila.nasaexplorer.core.domain.quiz.QuizQuestionSource
import com.radustavila.nasaexplorer.core.model.LunarLandingSite
import com.radustavila.nasaexplorer.core.model.LunarProgramme
import com.radustavila.nasaexplorer.core.model.QuizArg
import com.radustavila.nasaexplorer.core.model.QuizDifficulty
import com.radustavila.nasaexplorer.core.model.QuizPhrase
import com.radustavila.nasaexplorer.core.model.QuizQuestion
import com.radustavila.nasaexplorer.core.model.QuizText
import com.radustavila.nasaexplorer.core.model.QuizTopic
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import javax.inject.Inject
import kotlin.random.Random

/**
 * "Which mission landed here?" from the Moon map's landing sites (F25): the place and year of one
 * landing, the right mission and three that landed elsewhere (so the answer is never ambiguous when
 * two missions share a mare).
 */
class LandingSiteQuizSource @Inject constructor(
    private val repository: LunarLandingSiteRepository,
) : QuizQuestionSource {

    override val key: String = "moon-landing-sites"
    override val topics: Set<QuizTopic> = setOf(QuizTopic.MISSIONS)

    override suspend fun questions(random: Random, now: Instant): List<QuizQuestion> {
        val sites = repository.sites()
        val site = sites.randomOrNull(random)
        val wrong = sites.filter { it.region != site?.region }.shuffled(random).take(MAX_ANSWERS - 1)
        if (site == null || wrong.isEmpty()) return emptyList()
        val options = (wrong + site).shuffled(random)
        val easy = site.programme == LunarProgramme.APOLLO
        return listOf(
            QuizQuestion(
                id = "moon-landing-${site.id}",
                topic = QuizTopic.MISSIONS,
                difficulty = if (easy) QuizDifficulty.EASY else QuizDifficulty.MEDIUM,
                prompt = QuizText.Phrase(
                    QuizPhrase.LANDING_SITE_QUESTION,
                    QuizArg.Text(site.region),
                    QuizArg.Text(site.landedOn.year.toString()),
                ),
                answers = options.map { QuizText.Plain(it.mission) },
                correctIndex = options.indexOf(site),
                explanation = QuizText.Phrase(
                    QuizPhrase.LANDING_SITE_EXPLANATION,
                    QuizArg.Text(site.mission),
                    QuizArg.Text(site.region),
                    QuizArg.Day(site.landedAt),
                ),
                sourceUrl = site.nssdcaUrl,
            ),
        )
    }

    private val LunarLandingSite.landedAt: Instant
        get() = landedOn.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC)

    private companion object {
        const val MAX_ANSWERS = 4
    }
}
