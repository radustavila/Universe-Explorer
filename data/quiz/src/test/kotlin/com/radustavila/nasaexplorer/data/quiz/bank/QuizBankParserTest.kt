package com.radustavila.nasaexplorer.data.quiz.bank

import com.radustavila.nasaexplorer.core.domain.quiz.DailyQuizRules
import com.radustavila.nasaexplorer.core.model.QuizQuestionOrigin
import com.radustavila.nasaexplorer.core.model.QuizText
import com.radustavila.nasaexplorer.core.model.QuizTopic
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizBankParserTest {

    @Test
    fun `complete entries are read, broken ones skipped`() {
        val bank = QuizBankParser.parse(
            """
            {"version": 3, "questions": [
              {"id": "ok", "topic": "SOLAR_SYSTEM", "difficulty": "HARD", "question": " Q? ",
               "answers": ["A", "B"], "correct": 1,
               "explanation": "E.", "source": "https://science.nasa.gov/", "extra": true},
              {"id": "no-topic", "topic": "ASTROLOGY", "question": "Q?",
               "answers": ["A", "B"], "correct": 0, "explanation": "E."},
              {"id": "bad-index", "topic": "EARTH", "question": "Q?",
               "answers": ["A", "B"], "correct": 2, "explanation": "E."},
              {"id": "one-answer", "topic": "EARTH", "question": "Q?",
               "answers": ["A"], "correct": 0, "explanation": "E."},
              {"id": "twice", "topic": "EARTH", "question": "Q?",
               "answers": ["A", "A"], "correct": 0, "explanation": "E."},
              {"id": "ok", "topic": "EARTH", "question": "Duplicate id?",
               "answers": ["A", "B"], "correct": 0, "explanation": "E."},
              {"topic": "EARTH", "question": "No id?", "answers": ["A", "B"], "correct": 0, "explanation": "E."}
            ],
             "facts": [{"id": "f", "topic": "EARTH", "text": "Fact.", "source": "http://insecure.example"},
                       {"id": "g", "topic": "EARTH"}]}
            """.trimIndent(),
        )

        assertEquals(3, bank.version)
        val question = bank.questions.single()
        assertEquals("ok", question.id)
        assertEquals(QuizText.Plain("Q?"), question.prompt)
        assertEquals(1, question.correctIndex)
        assertEquals(QuizQuestionOrigin.BANK, question.origin)
        assertEquals("https://science.nasa.gov/", question.sourceUrl)
        val fact = bank.facts.single()
        assertEquals(null, fact.sourceUrl)
    }

    @Test
    fun `a file that is not a bank reads as an empty bank`() {
        assertTrue(QuizBankParser.parse("not json").questions.isEmpty())
        assertTrue(QuizBankParser.parse("").facts.isEmpty())
    }

    /** The shipped bank: every entry readable, ids unique, enough questions for the no-repeat rule. */
    @Test
    fun `the bundled English bank is complete`() {
        val file = File("src/main/assets/${AssetQuizBank.fileName(AssetQuizBank.DEFAULT_LANGUAGE)}")
        val text = file.readText()
        val raw = Regex("\"id\"\\s*:").findAll(text).count()

        val bank = QuizBankParser.parse(text)

        assertEquals("every entry parses", raw, bank.questions.size + bank.facts.size)
        assertTrue(bank.questions.size >= MIN_QUESTIONS)
        // The daily quiz walks the bank five places a day: this many questions never repeat within 30 days.
        assertTrue(bank.questions.size >= DailyQuizRules.NO_REPEAT_DAYS * DailyQuizRules.BANK_SLOTS_PER_DAY)
        assertEquals(QuizTopic.entries.toSet(), bank.questions.map { it.topic }.toSet())
        assertEquals(QuizTopic.entries.toSet(), bank.facts.map { it.topic }.toSet())
        assertTrue(bank.questions.all { it.sourceUrl != null })
        assertTrue(bank.facts.all { it.sourceUrl != null })
    }

    private companion object {
        const val MIN_QUESTIONS = 300
    }
}
