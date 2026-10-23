package com.radustavila.nasaexplorer.data.quiz.generator

import com.radustavila.nasaexplorer.core.common.dispatchers.AppDispatcher
import com.radustavila.nasaexplorer.core.common.dispatchers.Dispatcher
import com.radustavila.nasaexplorer.core.domain.quiz.QuizQuestionSource
import com.radustavila.nasaexplorer.core.domain.sky.SkyCatalogRepository
import com.radustavila.nasaexplorer.core.model.DeepSkyObject
import com.radustavila.nasaexplorer.core.model.DeepSkyType
import com.radustavila.nasaexplorer.core.model.QuizArg
import com.radustavila.nasaexplorer.core.model.QuizDifficulty
import com.radustavila.nasaexplorer.core.model.QuizPhrase
import com.radustavila.nasaexplorer.core.model.QuizQuestion
import com.radustavila.nasaexplorer.core.model.QuizText
import com.radustavila.nasaexplorer.core.model.QuizTopic
import java.time.Instant
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Questions from the bundled deep-sky catalogue (F23, OpenNGC): "What kind of object is M13?" and
 * "Which constellation holds M42?", over the Messier objects with a common name, so the
 * explanation can name them. Works offline from the first launch.
 */
class DeepSkyQuizSource @Inject constructor(
    private val catalog: SkyCatalogRepository,
    @Dispatcher(AppDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) : QuizQuestionSource {

    override val key: String = "deep-sky"

    override val topics: Set<QuizTopic> = setOf(QuizTopic.DEEP_SPACE, QuizTopic.NIGHT_SKY)

    override suspend fun questions(random: Random, now: Instant): List<QuizQuestion> {
        val sky = withContext(ioDispatcher) { catalog.catalog() }
        val names = sky.constellationNames()
        val candidates = sky.deepSky
            .filter { it.messier != null && it.commonName != null && it.constellation in names }
            .sortedBy { it.messier }
        if (candidates.size < 2) return emptyList()
        val (forType, forConstellation) = candidates.shuffled(random).take(2)
        return listOfNotNull(
            typeQuestion(forType, names, random),
            constellationQuestion(forConstellation, candidates, names, random),
        )
    }

    private fun typeQuestion(item: DeepSkyObject, names: Map<String, String>, random: Random): QuizQuestion? {
        if (item.type !in ASKED_TYPES) return null
        val options = (ASKED_TYPES.filter { it != item.type }.shuffled(random).take(MAX_ANSWERS - 1) + item.type)
            .shuffled(random)
        return QuizQuestion(
            id = "deep-sky-type-${item.id}",
            topic = QuizTopic.DEEP_SPACE,
            difficulty = QuizDifficulty.MEDIUM,
            prompt = QuizText.Phrase(QuizPhrase.DEEP_SKY_TYPE_QUESTION, QuizArg.Text(item.messierName())),
            answers = options.map { QuizText.Phrase(QuizPhrase.ANSWER_DEEP_SKY_TYPE, QuizArg.DeepSkyTypeName(it)) },
            correctIndex = options.indexOf(item.type),
            explanation = item.explanation(QuizPhrase.DEEP_SKY_TYPE_EXPLANATION, names),
            sourceUrl = item.nasaPageUrl,
        )
    }

    private fun constellationQuestion(
        item: DeepSkyObject,
        candidates: List<DeepSkyObject>,
        names: Map<String, String>,
        random: Random,
    ): QuizQuestion {
        val wrong = candidates.map { it.constellation }.distinct().filter { it != item.constellation }
            .shuffled(random).take(MAX_ANSWERS - 1)
        val options = (wrong + item.constellation).shuffled(random)
        return QuizQuestion(
            id = "deep-sky-constellation-${item.id}",
            topic = QuizTopic.NIGHT_SKY,
            difficulty = QuizDifficulty.MEDIUM,
            prompt = QuizText.Phrase(QuizPhrase.DEEP_SKY_CONSTELLATION_QUESTION, QuizArg.Text(item.messierName())),
            // Constellation names are Latin, the same in every language: data, not words.
            answers = options.map { QuizText.Plain(names.getValue(it)) },
            correctIndex = options.indexOf(item.constellation),
            explanation = item.explanation(QuizPhrase.DEEP_SKY_CONSTELLATION_EXPLANATION, names),
            sourceUrl = item.nasaPageUrl,
        )
    }

    /** "M42 is the Great Orion Nebula, in Orion." */
    private fun DeepSkyObject.explanation(phrase: QuizPhrase, names: Map<String, String>) = QuizText.Phrase(
        phrase,
        QuizArg.Text(messierName()),
        QuizArg.Text(checkNotNull(commonName)),
        QuizArg.Text(names.getValue(constellation)),
    )

    /** A catalogue designation, not a word. */
    private fun DeepSkyObject.messierName(): String = "M$messier"

    private companion object {
        const val MAX_ANSWERS = 4

        /** Kinds a beginner can tell apart; dark nebulae and star groups are too few to ask about. */
        val ASKED_TYPES = listOf(
            DeepSkyType.GALAXY,
            DeepSkyType.OPEN_CLUSTER,
            DeepSkyType.GLOBULAR_CLUSTER,
            DeepSkyType.PLANETARY_NEBULA,
            DeepSkyType.NEBULA,
            DeepSkyType.SUPERNOVA_REMNANT,
        )
    }
}
