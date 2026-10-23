package com.radustavila.nasaexplorer.feature.quiz.play

import androidx.lifecycle.SavedStateHandle
import com.radustavila.nasaexplorer.core.domain.usecase.DailyQuizUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.FinishDailyQuizUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.FinishPracticeQuizUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveQuizProgressUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.PracticeQuizUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.QuizTodayUseCase
import com.radustavila.nasaexplorer.core.model.QuizBadge
import com.radustavila.nasaexplorer.core.model.QuizProgress
import com.radustavila.nasaexplorer.core.model.QuizTopic
import com.radustavila.nasaexplorer.core.testing.data.TestQuiz
import com.radustavila.nasaexplorer.core.testing.fake.FakeQuizBank
import com.radustavila.nasaexplorer.core.testing.fake.FakeQuizClock
import com.radustavila.nasaexplorer.core.testing.fake.FakeQuizProgressRepository
import com.radustavila.nasaexplorer.core.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuizPlayViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeQuizClock()
    private val bank = FakeQuizBank(TestQuiz.bank(300))
    private val progress = FakeQuizProgressRepository()
    private val today = LocalDate.of(2026, 10, 23)

    private fun viewModel(topic: QuizTopic? = null) = QuizPlayViewModel(
        savedStateHandle = SavedStateHandle(mapOf("topic" to topic?.name)),
        dailyQuiz = DailyQuizUseCase(bank, emptySet(), clock),
        practiceQuiz = PracticeQuizUseCase(bank, emptySet(), progress, clock),
        finishDaily = FinishDailyQuizUseCase(progress),
        finishPractice = FinishPracticeQuizUseCase(progress, clock),
        observeProgress = ObserveQuizProgressUseCase(progress),
        today = QuizTodayUseCase(clock),
    )

    @Test
    fun `a daily round answers, shows whether each was right, moves on and records the result`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(5, state.questions.size)
        assertEquals(QuizMode.Daily(today), state.mode)

        repeat(5) { index ->
            val question = viewModel.uiState.value.current!!
            // Right on the first three, wrong on the last two.
            val pick = if (index < 3) question.correctIndex else (question.correctIndex + 1) % question.answers.size
            viewModel.onAnswer(pick)
            // A second tap on the same question changes nothing.
            viewModel.onAnswer(question.correctIndex)
            assertEquals(pick, viewModel.uiState.value.selected)
            viewModel.onNext()
        }
        advanceUntilIdle()

        val done = viewModel.uiState.value
        assertEquals(listOf(true, true, true, false, false), done.results)
        assertEquals(3, done.outcome!!.score)
        assertEquals(1, done.outcome!!.streak)
        assertEquals(setOf(QuizBadge.FIRST_QUIZ), done.outcome!!.newBadges)
        assertEquals(today, progress.current.lastDailyDate)
    }

    @Test
    fun `next does nothing before an answer`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNext()

        assertEquals(0, viewModel.uiState.value.index)
        assertNull(viewModel.uiState.value.selected)
    }

    @Test
    fun `today's quiz once played opens on its result`() = runTest {
        progress.update {
            QuizProgress(lastDailyDate = today, lastDailyAnswers = listOf(true, false, true, true, true), streak = 6)
        }

        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.questions.isEmpty())
        assertEquals(4, state.outcome!!.score)
        assertEquals(6, state.outcome!!.streak)
    }

    @Test
    fun `practice asks ten questions of the topic and does not touch the streak`() = runTest {
        val viewModel = viewModel(QuizTopic.EARTH)
        advanceUntilIdle()
        assertEquals(QuizMode.Practice(QuizTopic.EARTH), viewModel.uiState.value.mode)
        assertEquals(10, viewModel.uiState.value.questions.size)
        assertTrue(viewModel.uiState.value.questions.all { it.topic == QuizTopic.EARTH })

        repeat(10) {
            viewModel.onAnswer(viewModel.uiState.value.current!!.correctIndex)
            viewModel.onNext()
        }
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.outcome)
        assertEquals(10, progress.current.totalCorrect)
        assertNull(progress.current.lastDailyDate)
        assertEquals(setOf(QuizTopic.EARTH), progress.current.practicedTopics)
    }
}
