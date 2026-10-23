package com.radustavila.nasaexplorer.feature.quiz

import com.radustavila.nasaexplorer.core.model.DailyQuiz
import com.radustavila.nasaexplorer.core.model.MoonPhase
import com.radustavila.nasaexplorer.core.model.QuizArg
import com.radustavila.nasaexplorer.core.model.QuizDifficulty
import com.radustavila.nasaexplorer.core.model.QuizPhrase
import com.radustavila.nasaexplorer.core.model.QuizQuestion
import com.radustavila.nasaexplorer.core.model.QuizText
import com.radustavila.nasaexplorer.core.model.QuizTopic
import com.radustavila.nasaexplorer.core.testing.data.TestQuiz
import java.time.Instant
import java.time.LocalDate

/** A fixed daily quiz for screen tests and goldens: three bank questions and two made from data. */
internal object QuizFixtures {

    val DATE: LocalDate = LocalDate.of(2026, 10, 23)

    val venus = TestQuiz.bankQuestion()

    val gagarin = TestQuiz.bankQuestion(
        id = "human-001",
        topic = QuizTopic.HUMAN_SPACEFLIGHT,
        question = "Who was the first person to orbit Earth?",
        answers = listOf("Alan Shepard", "Yuri Gagarin", "John Glenn", "Valentina Tereshkova"),
        correctIndex = 1,
        explanation = "Yuri Gagarin circled Earth once aboard Vostok 1 on 12 April 1961, a flight of 108 minutes.",
    )

    val olympus = TestQuiz.bankQuestion(
        id = "sol-010",
        question = "On which world is Olympus Mons, the tallest volcano in the Solar System?",
        answers = listOf("Venus", "Io", "Mars", "Earth"),
        correctIndex = 2,
        explanation = "Olympus Mons on Mars rises about 22 km, nearly three times the height of Everest.",
    )

    val farthest = QuizQuestion(
        id = "farthest-spacecraft-test",
        topic = QuizTopic.MISSIONS,
        difficulty = QuizDifficulty.MEDIUM,
        prompt = QuizText.Phrase(QuizPhrase.FARTHEST_SPACECRAFT_QUESTION),
        answers = listOf("New Horizons", "Voyager 1", "Voyager 2").map(QuizText::Plain),
        correctIndex = 1,
        explanation = QuizText.Phrase(
            QuizPhrase.FARTHEST_SPACECRAFT_EXPLANATION,
            QuizArg.Text("Voyager 1"),
            QuizArg.LargeDistanceKm(25_830_000_000.0),
        ),
        sourceUrl = "https://ssd.jpl.nasa.gov/horizons/",
    )

    val moon = QuizQuestion(
        id = "moon-phase-test",
        topic = QuizTopic.NIGHT_SKY,
        difficulty = QuizDifficulty.EASY,
        prompt = QuizText.Phrase(QuizPhrase.MOON_PHASE_QUESTION),
        answers = listOf(
            MoonPhase.FULL_MOON,
            MoonPhase.FIRST_QUARTER,
            MoonPhase.WANING_CRESCENT,
            MoonPhase.NEW_MOON,
        ).map { QuizText.Phrase(QuizPhrase.ANSWER_MOON_PHASE, QuizArg.MoonPhaseName(it)) },
        correctIndex = 1,
        explanation = QuizText.Phrase(
            QuizPhrase.MOON_PHASE_EXPLANATION,
            QuizArg.Percent(0.58),
            QuizArg.Day(Instant.parse("2026-10-26T04:12:00Z")),
        ),
    )

    val questions = listOf(venus, farthest, gagarin, moon, olympus)

    val daily = DailyQuiz(DATE, questions)
}
