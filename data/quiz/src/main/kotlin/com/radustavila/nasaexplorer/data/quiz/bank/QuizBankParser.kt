package com.radustavila.nasaexplorer.data.quiz.bank

import com.radustavila.nasaexplorer.core.model.FunFact
import com.radustavila.nasaexplorer.core.model.QuizDifficulty
import com.radustavila.nasaexplorer.core.model.QuizQuestion
import com.radustavila.nasaexplorer.core.model.QuizQuestionOrigin
import com.radustavila.nasaexplorer.core.model.QuizText
import com.radustavila.nasaexplorer.core.model.QuizTopic
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The bank file (`quiz_bank_<lang>.json`): versioned, every field optional so one bad entry costs only itself. */
@Serializable
internal data class QuizBankDto(
    val version: Int = 0,
    val language: String? = null,
    val questions: List<QuizQuestionDto> = emptyList(),
    val facts: List<FunFactDto> = emptyList(),
)

@Serializable
internal data class QuizQuestionDto(
    val id: String? = null,
    val topic: String? = null,
    val difficulty: String? = null,
    val question: String? = null,
    val answers: List<String> = emptyList(),
    /** Index of the right answer in [answers]. */
    val correct: Int? = null,
    val explanation: String? = null,
    val source: String? = null,
)

@Serializable
internal data class FunFactDto(
    val id: String? = null,
    val topic: String? = null,
    val text: String? = null,
    val source: String? = null,
)

/** What the bank holds once read. */
data class ParsedQuizBank(
    val version: Int,
    val questions: List<QuizQuestion>,
    val facts: List<FunFact>,
)

/**
 * Reads the bank, keeping only entries that make a complete question or fact (a known topic, 2–4
 * distinct answers, a right answer among them, a unique id). A file that is not JSON at all reads
 * as an empty bank.
 */
object QuizBankParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): ParsedQuizBank {
        val dto = try {
            json.decodeFromString(QuizBankDto.serializer(), text)
        } catch (_: SerializationException) {
            QuizBankDto()
        } catch (_: IllegalArgumentException) {
            QuizBankDto()
        }
        return ParsedQuizBank(
            version = dto.version,
            questions = dto.questions.mapNotNull { it.toQuestion() }.distinctBy { it.id },
            facts = dto.facts.mapNotNull { it.toFact() }.distinctBy { it.id },
        )
    }

    private fun QuizQuestionDto.toQuestion(): QuizQuestion? {
        val id = id?.trim().orEmpty()
        val topic = QuizTopic.fromName(topic)
        val question = question?.trim().orEmpty()
        val explanation = explanation?.trim().orEmpty()
        val answers = answers.map { it.trim() }
        val correct = correct?.takeIf { it in answers.indices }
        val complete = id.isNotEmpty() && question.isNotEmpty() && explanation.isNotEmpty() &&
            answers.size in QuizQuestion.MIN_ANSWERS..QuizQuestion.MAX_ANSWERS &&
            answers.none { it.isEmpty() } &&
            answers.distinct().size == answers.size
        if (topic == null || correct == null || !complete) return null
        return QuizQuestion(
            id = id,
            topic = topic,
            difficulty = QuizDifficulty.fromName(difficulty) ?: QuizDifficulty.MEDIUM,
            prompt = QuizText.Plain(question),
            answers = answers.map(QuizText::Plain),
            correctIndex = correct,
            explanation = QuizText.Plain(explanation),
            sourceUrl = source?.trim()?.takeIf { it.startsWith("https://") },
            origin = QuizQuestionOrigin.BANK,
        )
    }

    private fun FunFactDto.toFact(): FunFact? {
        val id = id?.trim().orEmpty()
        val topic = QuizTopic.fromName(topic)
        val text = text?.trim().orEmpty()
        if (topic == null || id.isEmpty() || text.isEmpty()) return null
        return FunFact(
            id = id,
            topic = topic,
            text = QuizText.Plain(text),
            sourceUrl = source?.trim()?.takeIf { it.startsWith("https://") },
        )
    }
}
