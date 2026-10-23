package com.radustavila.nasaexplorer.feature.quiz.play

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.radustavila.nasaexplorer.core.domain.usecase.DailyQuizUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.FinishDailyQuizUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.FinishPracticeQuizUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveQuizProgressUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.PracticeQuizUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.QuizTodayUseCase
import com.radustavila.nasaexplorer.core.model.DailyQuiz
import com.radustavila.nasaexplorer.core.model.QuizOutcome
import com.radustavila.nasaexplorer.core.model.QuizQuestion
import com.radustavila.nasaexplorer.core.model.QuizTopic
import com.radustavila.nasaexplorer.feature.quiz.QuizPlayRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A daily quiz for [date], or practice of [topic]. */
@Immutable
sealed interface QuizMode {
    data class Daily(val date: LocalDate) : QuizMode

    data class Practice(val topic: QuizTopic) : QuizMode
}

/**
 * @property selected the answer picked for the current question, or null while it is open.
 * @property results whether each answered question was answered right, in order.
 * @property outcome set once the round is over.
 */
@Immutable
data class QuizPlayUiState(
    val isLoading: Boolean = true,
    val mode: QuizMode? = null,
    val questions: List<QuizQuestion> = emptyList(),
    val index: Int = 0,
    val selected: Int? = null,
    val results: List<Boolean> = emptyList(),
    val outcome: QuizOutcome? = null,
) {
    val current: QuizQuestion? get() = questions.getOrNull(index)
    val isAnswered: Boolean get() = selected != null
    val isLast: Boolean get() = index == questions.lastIndex
}

@HiltViewModel
class QuizPlayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val dailyQuiz: DailyQuizUseCase,
    private val practiceQuiz: PracticeQuizUseCase,
    private val finishDaily: FinishDailyQuizUseCase,
    private val finishPractice: FinishPracticeQuizUseCase,
    private val observeProgress: ObserveQuizProgressUseCase,
    private val today: QuizTodayUseCase,
) : ViewModel() {

    private val topic = QuizTopic.fromName(savedStateHandle.toRoute<QuizPlayRoute>().topic)
    private val state = MutableStateFlow(QuizPlayUiState())
    val uiState: StateFlow<QuizPlayUiState> = state.asStateFlow()

    private var daily: DailyQuiz? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        if (topic != null) {
            val questions = practiceQuiz(topic)
            state.value = QuizPlayUiState(isLoading = false, mode = QuizMode.Practice(topic), questions = questions)
            return
        }
        val date = today()
        val progress = observeProgress().first()
        val done = progress.dailyResultOn(date)
        if (done != null) {
            // Today's quiz is played once; opening it again shows how it went.
            state.value = QuizPlayUiState(
                isLoading = false,
                mode = QuizMode.Daily(date),
                results = done,
                outcome = QuizOutcome(done.count { it }, done.size, progress.streakOn(date), emptySet()),
            )
            return
        }
        val quiz = dailyQuiz(date).also { daily = it }
        state.value = QuizPlayUiState(isLoading = false, mode = QuizMode.Daily(date), questions = quiz.questions)
    }

    fun onAnswer(index: Int) {
        state.update { current ->
            val question = current.current ?: return@update current
            if (current.isAnswered || index !in question.answers.indices) return@update current
            current.copy(selected = index, results = current.results + (index == question.correctIndex))
        }
    }

    fun onNext() {
        val current = state.value
        if (!current.isAnswered || current.outcome != null) return
        if (!current.isLast) {
            state.value = current.copy(index = current.index + 1, selected = null)
            return
        }
        viewModelScope.launch {
            val outcome = when (val mode = current.mode) {
                is QuizMode.Daily -> finishDaily(checkNotNull(daily), current.results)
                is QuizMode.Practice -> finishPractice(mode.topic, current.questions, current.results)
                null -> return@launch
            }
            state.update { it.copy(outcome = outcome) }
        }
    }
}
