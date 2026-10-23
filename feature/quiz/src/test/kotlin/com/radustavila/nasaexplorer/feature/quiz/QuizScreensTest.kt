package com.radustavila.nasaexplorer.feature.quiz

import android.content.res.Resources
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.designsystem.notification.NotificationPermissionToggle
import com.radustavila.nasaexplorer.core.designsystem.units.UnitFormatter
import com.radustavila.nasaexplorer.core.model.MeasurementSystem
import com.radustavila.nasaexplorer.core.model.MoonPhase
import com.radustavila.nasaexplorer.core.model.Planet
import com.radustavila.nasaexplorer.core.model.QuizArg
import com.radustavila.nasaexplorer.core.model.QuizPhrase
import com.radustavila.nasaexplorer.core.model.QuizText
import com.radustavila.nasaexplorer.core.model.QuizTopic
import com.radustavila.nasaexplorer.core.model.UnitPreferences
import com.radustavila.nasaexplorer.core.testing.data.TestQuiz
import com.radustavila.nasaexplorer.feature.quiz.home.QUIZ_START_TEST_TAG
import com.radustavila.nasaexplorer.feature.quiz.home.QuizHomeContent
import com.radustavila.nasaexplorer.feature.quiz.home.QuizHomeUiState
import com.radustavila.nasaexplorer.feature.quiz.play.QUIZ_ANSWER_TEST_TAG
import com.radustavila.nasaexplorer.feature.quiz.play.QUIZ_EXPLANATION_TEST_TAG
import com.radustavila.nasaexplorer.feature.quiz.play.QUIZ_NEXT_TEST_TAG
import com.radustavila.nasaexplorer.feature.quiz.play.QuizMode
import com.radustavila.nasaexplorer.feature.quiz.play.QuizPlayContent
import com.radustavila.nasaexplorer.feature.quiz.play.QuizPlayUiState
import com.radustavila.nasaexplorer.feature.quiz.today.DID_YOU_KNOW_NEXT_TEST_TAG
import com.radustavila.nasaexplorer.feature.quiz.today.DailyQuizCardContent
import com.radustavila.nasaexplorer.feature.quiz.today.DailyQuizCardUiState
import com.radustavila.nasaexplorer.feature.quiz.today.DidYouKnowCardContent
import com.radustavila.nasaexplorer.feature.quiz.today.DidYouKnowUiState
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuizScreensTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val resources: Resources = ApplicationProvider.getApplicationContext<android.app.Application>().resources

    @Test
    fun `answering shows the explanation, then next moves on`() {
        var state by mutableStateOf(
            QuizPlayUiState(
                isLoading = false,
                mode = QuizMode.Daily(QuizFixtures.DATE),
                questions = QuizFixtures.questions,
            ),
        )
        composeTestRule.setContent {
            QuizPlayContent(
                uiState = state,
                onAnswer = {
                    state = state.copy(selected = it, results = state.results + (it == state.current!!.correctIndex))
                },
                onNext = { state = state.copy(index = state.index + 1, selected = null) },
                onCloseClick = {},
                onPracticeAgain = {},
            )
        }

        composeTestRule.onNodeWithText("Question 1 of 5").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(QUIZ_EXPLANATION_TEST_TAG).assertCountEquals(0)

        composeTestRule.onNodeWithText("Venus").performClick()

        // The explanation and the next button sit below the answers, off a small window.
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(QUIZ_NEXT_TEST_TAG))
        composeTestRule.onNodeWithText("Right!").assertIsDisplayed()
        composeTestRule.onNodeWithText((QuizFixtures.venus.explanation as QuizText.Plain).text).assertIsDisplayed()
        composeTestRule.onNodeWithTag(QUIZ_NEXT_TEST_TAG).performClick()

        composeTestRule.onNodeWithText("Question 2 of 5").assertIsDisplayed()
        composeTestRule.onNodeWithText("Which of these spacecraft is farthest from Earth right now?")
            .assertIsDisplayed()
        assertEquals(1, state.index)
        assertEquals(3, composeTestRule.onAllNodesWithTag(QUIZ_ANSWER_TEST_TAG).fetchSemanticsNodes().size)
    }

    @Test
    fun `the home starts today's quiz and opens a topic`() {
        var started = false
        var topic: QuizTopic? = null
        composeTestRule.setContent {
            QuizHomeContent(
                uiState = QuizHomeUiState(
                    isLoaded = true,
                    today = QuizFixtures.DATE,
                    topics = mapOf(QuizTopic.EARTH to 30),
                ),
                reminder = NotificationPermissionToggle(isOn = false, onClick = {}),
                onBackClick = {},
                onDailyClick = { started = true },
                onTopicClick = { topic = it },
            )
        }

        composeTestRule.onNodeWithTag(QUIZ_START_TEST_TAG).performClick()
        composeTestRule.onNodeWithText("Earth from space").performScrollTo().performClick()

        assertEquals(true, started)
        assertEquals(QuizTopic.EARTH, topic)
    }

    @Test
    fun `the daily card plays while the quiz waits and opens the quiz once done`() {
        var state by mutableStateOf(DailyQuizCardUiState(isLoaded = true))
        var played = 0
        var opened = 0
        composeTestRule.setContent {
            DailyQuizCardContent(state, onPlayClick = { played++ }, onOpenClick = { opened++ })
        }

        composeTestRule.onNodeWithText("Five new space questions are waiting.").performClick()
        state = DailyQuizCardUiState(isLoaded = true, todayResult = listOf(true, true, true, false, true), streak = 2)
        composeTestRule.onNodeWithText("Today: 4/5. See you tomorrow!").performClick()

        assertEquals(1, played)
        assertEquals(1, opened)
        composeTestRule.onNodeWithText("2-day streak").assertIsDisplayed()
    }

    @Test
    fun `the fact card steps through the day's facts`() {
        val facts = listOf(TestQuiz.fact(text = "First."), TestQuiz.fact(id = "2", text = "Second."))
        var state by mutableStateOf(DidYouKnowUiState(facts = facts))
        composeTestRule.setContent {
            DidYouKnowCardContent(state, onNextClick = { state = state.copy(index = (state.index + 1) % facts.size) })
        }

        composeTestRule.onNodeWithText("First.").assertIsDisplayed()
        composeTestRule.onNodeWithTag(DID_YOU_KNOW_NEXT_TEST_TAG).performClick()
        composeTestRule.onNodeWithText("Second.").assertIsDisplayed()
    }

    @Test
    fun `phrases are worded from resources with formatted values and plurals`() {
        val metric = UnitPreferences(measurementSystem = MeasurementSystem.METRIC)
        val units = UnitFormatter.create(ApplicationProvider.getApplicationContext(), metric, Locale.UK)
        val format = QuizTextFormatter(resources, units)
        fun phrase(phrase: QuizPhrase, vararg args: QuizArg) = format.format(QuizText.Phrase(phrase, *args))

        val crescent = QuizArg.MoonPhaseName(MoonPhase.WANING_CRESCENT)
        assertEquals("Waning crescent", phrase(QuizPhrase.ANSWER_MOON_PHASE, crescent))
        assertEquals("Jupiter", phrase(QuizPhrase.ANSWER_PLANET, QuizArg.PlanetName(Planet.JUPITER)))
        assertEquals("1 person is in space right now.", phrase(QuizPhrase.FACT_PEOPLE_IN_SPACE, QuizArg.Count(1)))
        assertEquals("12 people are in space right now.", phrase(QuizPhrase.FACT_PEOPLE_IN_SPACE, QuizArg.Count(12)))
        assertEquals(
            "NASA's Exoplanet Archive lists 6,128 confirmed planets around other stars.",
            format.format(QuizText.Phrase(QuizPhrase.FACT_CONFIRMED_EXOPLANETS, QuizArg.Count(6_128))),
        )
        assertEquals(
            "Voyager 1 is 25.8 billion km from Earth right now, farther than any other spacecraft.",
            phrase(
                QuizPhrase.FACT_SPACECRAFT_DISTANCE,
                QuizArg.Text("Voyager 1"),
                QuizArg.LargeDistanceKm(25_830_000_000.0),
            ),
        )
        assertEquals("Plain words.", format.format(QuizText.Plain("Plain words.")))
        // Every phrase has wording.
        QuizPhrase.entries.forEach { phrase ->
            format.format(QuizText.Phrase(phrase, QuizArg.Count(2), QuizArg.Text("A"), QuizArg.Day(Instant.EPOCH)))
        }
    }

    @Test
    fun `the share text gives the date, the score and one square per answer`() {
        val results = listOf(true, true, false, true, true)
        val text = QuizShareText.daily(resources, "NASA Explorer", LocalDate.of(2026, 10, 23), results, Locale.UK)

        assertEquals("NASA Explorer daily quiz, 23 Oct: 4/5\n🟩🟩🟥🟩🟩", text)
    }
}
