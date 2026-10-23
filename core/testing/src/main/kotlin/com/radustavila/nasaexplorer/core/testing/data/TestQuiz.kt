package com.radustavila.nasaexplorer.core.testing.data

import com.radustavila.nasaexplorer.core.model.FunFact
import com.radustavila.nasaexplorer.core.model.QuizArg
import com.radustavila.nasaexplorer.core.model.QuizDifficulty
import com.radustavila.nasaexplorer.core.model.QuizPhrase
import com.radustavila.nasaexplorer.core.model.QuizQuestion
import com.radustavila.nasaexplorer.core.model.QuizQuestionOrigin
import com.radustavila.nasaexplorer.core.model.QuizText
import com.radustavila.nasaexplorer.core.model.QuizTopic

/** Quiz questions and facts for tests. */
object TestQuiz {

    fun bankQuestion(
        id: String = "sol-001",
        topic: QuizTopic = QuizTopic.SOLAR_SYSTEM,
        difficulty: QuizDifficulty = QuizDifficulty.EASY,
        question: String = "Which planet has the longest day?",
        answers: List<String> = listOf("Venus", "Mercury", "Jupiter", "Mars"),
        correctIndex: Int = 0,
        explanation: String = "Venus turns once every 243 Earth days, longer than its 225-day year.",
        sourceUrl: String? = "https://science.nasa.gov/venus/venus-facts/",
    ): QuizQuestion = QuizQuestion(
        id = id,
        topic = topic,
        difficulty = difficulty,
        prompt = QuizText.Plain(question),
        answers = answers.map(QuizText::Plain),
        correctIndex = correctIndex,
        explanation = QuizText.Plain(explanation),
        sourceUrl = sourceUrl,
        origin = QuizQuestionOrigin.BANK,
    )

    /** [count] bank questions "bank-000"…, spread over every topic. */
    fun bank(count: Int): List<QuizQuestion> = (0 until count).map { index ->
        bankQuestion(
            id = "bank-%03d".format(index),
            topic = QuizTopic.entries[index % QuizTopic.entries.size],
            question = "Question $index?",
            answers = listOf("Right $index", "Wrong A", "Wrong B", "Wrong C"),
            explanation = "Explanation $index.",
        )
    }

    fun generatedQuestion(
        id: String = "people-in-space-10",
        topic: QuizTopic = QuizTopic.HUMAN_SPACEFLIGHT,
        correctIndex: Int = 1,
    ): QuizQuestion = QuizQuestion(
        id = id,
        topic = topic,
        difficulty = QuizDifficulty.MEDIUM,
        prompt = QuizText.Phrase(QuizPhrase.PEOPLE_IN_SPACE_QUESTION),
        answers = PEOPLE_COUNTS.map { QuizText.Phrase(QuizPhrase.ANSWER_COUNT, QuizArg.Count(it)) },
        correctIndex = correctIndex,
        explanation = QuizText.Phrase(
            QuizPhrase.PEOPLE_IN_SPACE_EXPLANATION,
            QuizArg.Count(PEOPLE_COUNTS[1]),
            QuizArg.Text("Sunita Williams"),
        ),
    )

    fun fact(id: String = "fact-sol-01", text: String = "A day on Venus is longer than its year."): FunFact =
        FunFact(
            id = id,
            topic = QuizTopic.SOLAR_SYSTEM,
            text = QuizText.Plain(text),
            sourceUrl = "https://science.nasa.gov/venus/",
        )

    /** The answers of [generatedQuestion]; the second is right by default. */
    private val PEOPLE_COUNTS = listOf(7, 10, 13, 4)
}
