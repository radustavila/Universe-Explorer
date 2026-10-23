package com.radustavila.nasaexplorer.data.quiz.generator

import com.radustavila.nasaexplorer.core.domain.quiz.QuizQuestionSource
import com.radustavila.nasaexplorer.core.domain.repository.NasaMediaRepository
import com.radustavila.nasaexplorer.core.model.NasaMedia
import com.radustavila.nasaexplorer.core.model.NasaMediaType
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
 * "Hubble or Webb?" over the gallery's cached pictures (F3). A picture only qualifies when its
 * title, description and keywords name exactly one of the two telescopes: comparisons and
 * pictures that name neither are left out.
 */
class GalleryQuizSource @Inject constructor(
    private val repository: NasaMediaRepository,
) : QuizQuestionSource {

    override val key: String = "gallery"

    override val topics: Set<QuizTopic> = setOf(QuizTopic.MISSIONS)

    override suspend fun questions(random: Random, now: Instant): List<QuizQuestion> {
        val pictures = repository.observeRails().first().values.flatten()
            .distinctBy { it.nasaId }
            .filter { it.mediaType == NasaMediaType.IMAGE && it.previewUrl != null }
        val byTelescope = pictures.groupBy(::telescopeOf)
        // One of each telescope when both are cached, so the answer is not always the same.
        return Telescope.entries.mapNotNull { telescope ->
            byTelescope[telescope]?.random(random)?.let { question(it, telescope) }
        }.shuffled(random)
    }

    private fun question(media: NasaMedia, telescope: Telescope) = QuizQuestion(
        id = "hubble-or-webb-${media.nasaId}",
        topic = QuizTopic.MISSIONS,
        difficulty = QuizDifficulty.MEDIUM,
        prompt = QuizText.Phrase(QuizPhrase.HUBBLE_OR_WEBB_QUESTION),
        answers = listOf(QuizText.Phrase(QuizPhrase.ANSWER_HUBBLE), QuizText.Phrase(QuizPhrase.ANSWER_WEBB)),
        correctIndex = telescope.ordinal,
        explanation = QuizText.Phrase(
            when (telescope) {
                Telescope.HUBBLE -> QuizPhrase.HUBBLE_OR_WEBB_EXPLANATION_HUBBLE
                Telescope.WEBB -> QuizPhrase.HUBBLE_OR_WEBB_EXPLANATION_WEBB
            },
            QuizArg.Text(media.title),
        ),
        sourceUrl = "https://images.nasa.gov/details/" + URLEncoder.encode(media.nasaId, Charsets.UTF_8.name()),
        imageUrl = media.previewUrl,
    )

    /** In answer order: Hubble first, Webb second. */
    enum class Telescope {
        HUBBLE,
        WEBB,
    }

    companion object {
        private val WEBB_NAMES = listOf("webb", "jwst")
        private const val HUBBLE_NAME = "hubble"

        internal fun telescopeOf(media: NasaMedia): Telescope? {
            val text = (listOf(media.title, media.description) + media.keywords).joinToString(" ").lowercase()
            val webb = WEBB_NAMES.any { it in text }
            val hubble = HUBBLE_NAME in text
            return when {
                webb && !hubble -> Telescope.WEBB
                hubble && !webb -> Telescope.HUBBLE
                else -> null
            }
        }
    }
}
