package com.radustavila.nasaexplorer.feature.quiz.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveQuizProgressUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.QuizTodayUseCase
import com.radustavila.nasaexplorer.feature.quiz.R
import com.radustavila.nasaexplorer.feature.quiz.play.ResultSquares
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

const val DAILY_QUIZ_CARD_TEST_TAG = "daily-quiz-card"

/**
 * "Five new space questions are waiting", or today's score once played, with the streak. Opens
 * today's quiz while it is waiting ([onPlayClick]), the quiz's home once it is done ([onOpenClick]).
 */
@Composable
fun DailyQuizCard(
    onPlayClick: () -> Unit,
    onOpenClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DailyQuizCardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DailyQuizCardContent(uiState = uiState, onPlayClick = onPlayClick, onOpenClick = onOpenClick, modifier = modifier)
}

@Composable
internal fun DailyQuizCardContent(
    uiState: DailyQuizCardUiState,
    onPlayClick: () -> Unit,
    onOpenClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!uiState.isLoaded) return
    val result = uiState.todayResult
    Card(
        onClick = if (result == null) onPlayClick else onOpenClick,
        modifier = modifier.fillMaxWidth().testTag(DAILY_QUIZ_CARD_TEST_TAG),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Quiz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    SectionTitle(stringResource(R.string.quiz_today_card_title))
                }
                if (result == null) {
                    Text(stringResource(R.string.quiz_today_card_waiting), style = MaterialTheme.typography.titleSmall)
                } else {
                    Text(
                        stringResource(R.string.quiz_today_card_done, result.count { it }, result.size),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    ResultSquares(result)
                }
                if (uiState.streak > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.LocalFireDepartment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            pluralStringResource(R.plurals.quiz_today_card_streak, uiState.streak, uiState.streak),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(R.string.quiz_today_card_open),
            )
        }
    }
}

@Immutable
data class DailyQuizCardUiState(
    val isLoaded: Boolean = false,
    /** Today's answers once played, else null. */
    val todayResult: List<Boolean>? = null,
    val streak: Int = 0,
)

@HiltViewModel
class DailyQuizCardViewModel @Inject constructor(
    observeProgress: ObserveQuizProgressUseCase,
    private val today: QuizTodayUseCase,
) : ViewModel() {

    val uiState: StateFlow<DailyQuizCardUiState> = observeProgress().map { progress ->
        val date = today()
        DailyQuizCardUiState(
            isLoaded = true,
            todayResult = progress.dailyResultOn(date),
            streak = progress.streakOn(date),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = DailyQuizCardUiState(),
    )

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
