package com.radustavila.nasaexplorer.feature.quiz.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveQuizProgressUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveQuizTopicsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.QuizTodayUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetQuizReminderEnabledUseCase
import com.radustavila.nasaexplorer.core.model.QuizProgress
import com.radustavila.nasaexplorer.core.model.QuizTopic
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class QuizHomeUiState(
    val isLoaded: Boolean = false,
    val today: LocalDate? = null,
    val progress: QuizProgress = QuizProgress(),
    /** How many bank questions each topic has, in display order. */
    val topics: Map<QuizTopic, Int> = emptyMap(),
) {
    val todayResult: List<Boolean>? get() = today?.let(progress::dailyResultOn)
    val streak: Int get() = today?.let(progress::streakOn) ?: 0
}

@HiltViewModel
class QuizHomeViewModel @Inject constructor(
    observeProgress: ObserveQuizProgressUseCase,
    observeTopics: ObserveQuizTopicsUseCase,
    private val today: QuizTodayUseCase,
    private val setReminderEnabled: SetQuizReminderEnabledUseCase,
) : ViewModel() {

    private val topics = flow { emit(observeTopics()) }

    val uiState: StateFlow<QuizHomeUiState> = combine(observeProgress(), topics) { progress, counts ->
        QuizHomeUiState(isLoaded = true, today = today(), progress = progress, topics = counts)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = QuizHomeUiState(),
    )

    fun onReminderChange(enabled: Boolean) {
        viewModelScope.launch { setReminderEnabled(enabled) }
    }

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
