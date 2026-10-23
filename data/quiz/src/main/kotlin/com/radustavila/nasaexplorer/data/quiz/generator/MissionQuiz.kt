package com.radustavila.nasaexplorer.data.quiz.generator

import com.radustavila.nasaexplorer.core.domain.quiz.FunFactSource
import com.radustavila.nasaexplorer.core.domain.quiz.QuizQuestionSource
import com.radustavila.nasaexplorer.core.domain.repository.CometRepository
import com.radustavila.nasaexplorer.core.domain.repository.DeepSpaceNetworkRepository
import com.radustavila.nasaexplorer.core.domain.repository.LaunchRepository
import com.radustavila.nasaexplorer.core.model.Comet
import com.radustavila.nasaexplorer.core.model.FunFact
import com.radustavila.nasaexplorer.core.model.QuizArg
import com.radustavila.nasaexplorer.core.model.QuizDifficulty
import com.radustavila.nasaexplorer.core.model.QuizPhrase
import com.radustavila.nasaexplorer.core.model.QuizQuestion
import com.radustavila.nasaexplorer.core.model.QuizText
import com.radustavila.nasaexplorer.core.model.QuizTopic
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.first

private const val MAX_ANSWERS = 4

/** "Which spacecraft is farthest from Earth right now?" over Horizons' far probes (F13). */
class DeepSpaceQuizSource @Inject constructor(
    private val repository: DeepSpaceNetworkRepository,
) : QuizQuestionSource {

    override val key: String = "deep-space"

    override val topics: Set<QuizTopic> = setOf(QuizTopic.MISSIONS)

    override suspend fun questions(random: Random, now: Instant): List<QuizQuestion> {
        val probes = repository.observeFarthestProbes().first().distinctBy { it.name }
        if (probes.size < MIN_PROBES) return emptyList()
        val options = probes.shuffled(random).take(MAX_ANSWERS)
        val farthest = options.maxBy { it.distanceAuAt(now) }
        return listOf(
            QuizQuestion(
                id = "farthest-spacecraft-" + options.map { it.name }.sorted().joinToString("|"),
                topic = QuizTopic.MISSIONS,
                difficulty = QuizDifficulty.MEDIUM,
                prompt = QuizText.Phrase(QuizPhrase.FARTHEST_SPACECRAFT_QUESTION),
                answers = options.map { QuizText.Plain(it.name) },
                correctIndex = options.indexOf(farthest),
                explanation = QuizText.Phrase(
                    QuizPhrase.FARTHEST_SPACECRAFT_EXPLANATION,
                    QuizArg.Text(farthest.name),
                    QuizArg.LargeDistanceKm(farthest.distanceKmAt(now)),
                ),
                sourceUrl = HORIZONS_PAGE,
            ),
        )
    }

    private companion object {
        const val MIN_PROBES = 3
    }
}

/** "Voyager 1 is 25.3 billion km from Earth right now." */
class DeepSpaceFunFactSource @Inject constructor(
    private val repository: DeepSpaceNetworkRepository,
) : FunFactSource {

    override val key: String = "deep-space"

    override suspend fun facts(now: Instant): List<FunFact> {
        val probes = repository.observeFarthestProbes().first()
        val farthest = probes.maxByOrNull { it.distanceAuAt(now) } ?: return emptyList()
        return listOf(
            FunFact(
                id = "live-farthest-spacecraft",
                topic = QuizTopic.MISSIONS,
                text = QuizText.Phrase(
                    QuizPhrase.FACT_SPACECRAFT_DISTANCE,
                    QuizArg.Text(farthest.name),
                    QuizArg.LargeDistanceKm(farthest.distanceKmAt(now)),
                ),
                sourceUrl = HORIZONS_PAGE,
            ),
        )
    }
}

/**
 * "Which comet is predicted to be brightest this month?" over the cached ephemerides (F18): each
 * comet's brightest predicted magnitude within [WINDOW] of now. Comets closer than half a magnitude
 * to the brightest are left out, as predictions are rarely better than that.
 */
class CometQuizSource @Inject constructor(
    private val repository: CometRepository,
) : QuizQuestionSource {

    override val key: String = "comets"

    override val topics: Set<QuizTopic> = setOf(QuizTopic.SOLAR_SYSTEM)

    override suspend fun questions(random: Random, now: Instant): List<QuizQuestion> {
        val peaks = repository.observeComets().first()
            .mapNotNull { comet -> peakMagnitude(comet, now)?.let { comet to it } }
            .distinctBy { it.first.name }
        val brightest = peaks.minByOrNull { it.second }
        // A single comet, or several too alike, leaves no clear wrong answer.
        val others = peaks.filter { brightest != null && it.second - brightest.second >= MIN_MAGNITUDE_GAP }
        if (brightest == null || others.isEmpty()) return emptyList()
        val options = (others.shuffled(random).take(MAX_ANSWERS - 1) + brightest).shuffled(random)
        return listOf(
            QuizQuestion(
                id = "brightest-comet-" + options.map { it.first.designation }.sorted().joinToString("|"),
                topic = QuizTopic.SOLAR_SYSTEM,
                difficulty = QuizDifficulty.HARD,
                prompt = QuizText.Phrase(QuizPhrase.BRIGHTEST_COMET_QUESTION),
                answers = options.map { QuizText.Plain(it.first.name) },
                correctIndex = options.indexOf(brightest),
                explanation = QuizText.Phrase(
                    QuizPhrase.BRIGHTEST_COMET_EXPLANATION,
                    QuizArg.Text(brightest.first.name),
                    QuizArg.Magnitude(brightest.second),
                ),
                sourceUrl = SBDB_LOOKUP + brightest.first.designation.replace(" ", "%20"),
            ),
        )
    }

    private fun peakMagnitude(comet: Comet, now: Instant): Double? = comet.ephemeris
        .filter { Duration.between(now, it.time).abs() <= WINDOW }
        .mapNotNull { it.magnitude }
        .minOrNull()

    private companion object {
        const val MIN_MAGNITUDE_GAP = 0.5
        val WINDOW: Duration = Duration.ofDays(15)
    }
}

/** "Which rocket is scheduled to launch next?" over the cached upcoming launches (F2). */
class LaunchQuizSource @Inject constructor(
    private val repository: LaunchRepository,
) : QuizQuestionSource {

    override val key: String = "launches"

    override val topics: Set<QuizTopic> = setOf(QuizTopic.MISSIONS)

    override suspend fun questions(random: Random, now: Instant): List<QuizQuestion> {
        val upcoming = repository.observeUpcomingLaunches().first().filter { it.net.isAfter(now) }.sortedBy { it.net }
        val next = upcoming.firstOrNull()
        val otherRockets = upcoming.map { it.rocket }.distinct().filter { it != next?.rocket }
        if (next == null || otherRockets.size < MIN_OTHER_ROCKETS) return emptyList()
        val options = (otherRockets.shuffled(random).take(MAX_ANSWERS - 1) + next.rocket).shuffled(random)
        return listOf(
            QuizQuestion(
                id = "next-launch-${next.id}",
                topic = QuizTopic.MISSIONS,
                difficulty = QuizDifficulty.MEDIUM,
                prompt = QuizText.Phrase(QuizPhrase.NEXT_LAUNCH_QUESTION),
                answers = options.map { QuizText.Plain(it) },
                correctIndex = options.indexOf(next.rocket),
                explanation = QuizText.Phrase(
                    QuizPhrase.NEXT_LAUNCH_EXPLANATION,
                    QuizArg.Text(next.name),
                    QuizArg.DayAndTime(next.net),
                    QuizArg.Text(next.pad),
                ),
                sourceUrl = next.pageUrl.takeIf { it.startsWith("https://") },
            ),
        )
    }

    private companion object {
        const val MIN_OTHER_ROCKETS = 2
    }
}

/**
 * "Did this booster land?" over the cached past launches (F27): a reusable first stage whose
 * landing was tried and whose outcome Launch Library 2 reports.
 */
class BoosterLandingQuizSource @Inject constructor(
    private val repository: LaunchRepository,
) : QuizQuestionSource {

    override val key: String = "booster-landings"

    override val topics: Set<QuizTopic> = setOf(QuizTopic.MISSIONS)

    override suspend fun questions(random: Random, now: Instant): List<QuizQuestion> {
        val landings = repository.observePastLaunches().first()
            .filter { it.net.isBefore(now) }
            .flatMap { launch -> launch.boosters.map { launch to it } }
            .filter { (_, booster) ->
                val landing = booster.landing
                booster.serial != null && landing?.attempt == true && landing.success != null &&
                    landing.locationName != null
            }
        val (launch, booster) = landings.randomOrNull(random) ?: return emptyList()
        val landing = checkNotNull(booster.landing)
        val landed = landing.success == true
        val serial = QuizArg.Text(checkNotNull(booster.serial))
        return listOf(
            QuizQuestion(
                id = "booster-landed-${launch.id}-${booster.serial}",
                topic = QuizTopic.MISSIONS,
                difficulty = QuizDifficulty.MEDIUM,
                prompt = QuizText.Phrase(
                    QuizPhrase.BOOSTER_LANDED_QUESTION,
                    serial,
                    QuizArg.Text(launch.name),
                    QuizArg.Day(launch.net),
                ),
                answers = listOf(QuizText.Phrase(QuizPhrase.ANSWER_YES), QuizText.Phrase(QuizPhrase.ANSWER_NO)),
                correctIndex = if (landed) 0 else 1,
                explanation = QuizText.Phrase(
                    if (landed) QuizPhrase.BOOSTER_LANDED_EXPLANATION_YES else QuizPhrase.BOOSTER_LANDED_EXPLANATION_NO,
                    serial,
                    QuizArg.Text(checkNotNull(landing.locationName)),
                ),
                sourceUrl = launch.pageUrl.takeIf { it.startsWith("https://") },
            ),
        )
    }
}

private const val HORIZONS_PAGE = "https://ssd.jpl.nasa.gov/horizons/"
private const val SBDB_LOOKUP = "https://ssd.jpl.nasa.gov/tools/sbdb_lookup.html#/?sstr="
