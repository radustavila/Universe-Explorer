package com.radustavila.nasaexplorer.feature.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.designsystem.notification.NotificationPermissionToggle
import com.radustavila.nasaexplorer.core.model.QuizBadge
import com.radustavila.nasaexplorer.core.model.QuizOutcome
import com.radustavila.nasaexplorer.core.model.QuizProgress
import com.radustavila.nasaexplorer.core.model.QuizTopic
import com.radustavila.nasaexplorer.core.screenshottesting.ScreenshotDevices
import com.radustavila.nasaexplorer.core.screenshottesting.captureComponent
import com.radustavila.nasaexplorer.core.screenshottesting.captureScreen
import com.radustavila.nasaexplorer.core.testing.data.TestQuiz
import com.radustavila.nasaexplorer.feature.quiz.home.QuizHomeContent
import com.radustavila.nasaexplorer.feature.quiz.home.QuizHomeUiState
import com.radustavila.nasaexplorer.feature.quiz.play.QuizMode
import com.radustavila.nasaexplorer.feature.quiz.play.QuizPlayContent
import com.radustavila.nasaexplorer.feature.quiz.play.QuizPlayUiState
import com.radustavila.nasaexplorer.feature.quiz.today.DailyQuizCardContent
import com.radustavila.nasaexplorer.feature.quiz.today.DailyQuizCardUiState
import com.radustavila.nasaexplorer.feature.quiz.today.DidYouKnowCardContent
import com.radustavila.nasaexplorer.feature.quiz.today.DidYouKnowUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden images of the quiz over fixed data; `./gradlew recordRoborazziDebug` rewrites them. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuizScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val homeState = QuizHomeUiState(
        isLoaded = true,
        today = QuizFixtures.DATE,
        progress = QuizProgress(
            lastDailyDate = QuizFixtures.DATE.minusDays(1),
            lastDailyAnswers = listOf(true, true, false, true, true),
            streak = 4,
            longestStreak = 9,
            bestScore = 5,
            quizzesCompleted = 23,
            totalCorrect = 87,
            badges = setOf(QuizBadge.FIRST_QUIZ, QuizBadge.STREAK_3, QuizBadge.STREAK_7, QuizBadge.QUIZZES_10),
        ),
        topics = mapOf(
            QuizTopic.SOLAR_SYSTEM to 60,
            QuizTopic.SUN_AND_STARS to 35,
            QuizTopic.DEEP_SPACE to 35,
            QuizTopic.EXOPLANETS to 25,
            QuizTopic.HUMAN_SPACEFLIGHT to 45,
            QuizTopic.MISSIONS to 45,
            QuizTopic.EARTH to 30,
            QuizTopic.NIGHT_SKY to 25,
        ),
    )

    /** The second question, answered wrong, with its explanation showing. */
    private val answeredState = QuizPlayUiState(
        isLoading = false,
        mode = QuizMode.Daily(QuizFixtures.DATE),
        questions = QuizFixtures.questions,
        index = 1,
        selected = 2,
        results = listOf(true, false),
    )

    private val resultState = QuizPlayUiState(
        isLoading = false,
        mode = QuizMode.Daily(QuizFixtures.DATE),
        questions = QuizFixtures.questions,
        index = 4,
        selected = 2,
        results = listOf(true, false, true, true, true),
        outcome = QuizOutcome(score = 4, total = 5, streak = 5, newBadges = setOf(QuizBadge.STREAK_3)),
    )

    private val reminderOn = NotificationPermissionToggle(isOn = true, onClick = {})

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun homeCompact() {
        composeTestRule.captureScreen("QuizHomeCompact") { QuizHomeContent(homeState, reminderOn, {}, {}, {}) }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.EXPANDED)
    fun homeExpanded() {
        composeTestRule.captureScreen("QuizHomeExpanded") { QuizHomeContent(homeState, reminderOn, {}, {}, {}) }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun questionCompact() {
        composeTestRule.captureScreen("QuizQuestionCompact") { QuizPlayContent(answeredState, {}, {}, {}, {}) }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.EXPANDED)
    fun questionExpanded() {
        composeTestRule.captureScreen("QuizQuestionExpanded") { QuizPlayContent(answeredState, {}, {}, {}, {}) }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun openQuestionWithPicture() {
        val pictureQuestion = TestQuiz.bankQuestion()
            .copy(imageUrl = "https://images-assets.nasa.gov/image/PIA26702/PIA26702~large.jpg")
        val state = QuizPlayUiState(
            isLoading = false,
            mode = QuizMode.Practice(QuizTopic.MISSIONS),
            questions = listOf(pictureQuestion),
        )
        composeTestRule.captureScreen("QuizQuestionPicture") { QuizPlayContent(state, {}, {}, {}, {}) }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun resultCompact() {
        composeTestRule.captureScreen("QuizResultCompact") { QuizPlayContent(resultState, {}, {}, {}, {}) }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.EXPANDED)
    fun resultExpanded() {
        composeTestRule.captureScreen("QuizResultExpanded") { QuizPlayContent(resultState, {}, {}, {}, {}) }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.COMPACT)
    fun todayCardsCompact() {
        composeTestRule.captureComponent("QuizTodayCardsCompact", width = 380.dp) { TodayCards() }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.EXPANDED)
    fun todayCardsExpanded() {
        composeTestRule.captureComponent("QuizTodayCardsExpanded", width = 720.dp) { TodayCards() }
    }

    @androidx.compose.runtime.Composable
    private fun TodayCards() {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DailyQuizCardContent(DailyQuizCardUiState(isLoaded = true, streak = 4), {}, {})
            DailyQuizCardContent(
                DailyQuizCardUiState(isLoaded = true, todayResult = listOf(true, true, false, true, true), streak = 5),
                {},
                {},
            )
            DidYouKnowCardContent(
                DidYouKnowUiState(
                    facts = listOf(
                        TestQuiz.fact(text = "All the asteroids together have less mass than our Moon."),
                        TestQuiz.fact(id = "fact-2"),
                    ),
                ),
                onNextClick = {},
            )
        }
    }
}
