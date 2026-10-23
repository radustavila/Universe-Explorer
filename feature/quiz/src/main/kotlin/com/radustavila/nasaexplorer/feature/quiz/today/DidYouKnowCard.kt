package com.radustavila.nasaexplorer.feature.quiz.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.designsystem.component.SectionTitle
import com.radustavila.nasaexplorer.core.designsystem.util.rememberCustomTabOpener
import com.radustavila.nasaexplorer.core.domain.usecase.DailyFunFactsUseCase
import com.radustavila.nasaexplorer.core.model.FunFact
import com.radustavila.nasaexplorer.feature.quiz.FunFactIcon
import com.radustavila.nasaexplorer.feature.quiz.R
import com.radustavila.nasaexplorer.feature.quiz.rememberQuizTextFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val DID_YOU_KNOW_CARD_TEST_TAG = "did-you-know-card"
const val DID_YOU_KNOW_NEXT_TEST_TAG = "did-you-know-next"

/** Today's "Did you know" line, the same all day, with a button for another and a link to its source. */
@Composable
fun DidYouKnowCard(
    modifier: Modifier = Modifier,
    viewModel: DidYouKnowViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DidYouKnowCardContent(uiState = uiState, onNextClick = viewModel::onNextFact, modifier = modifier)
}

@Composable
internal fun DidYouKnowCardContent(
    uiState: DidYouKnowUiState,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fact = uiState.fact ?: return
    val format = rememberQuizTextFormatter()
    val openUrl = rememberCustomTabOpener()
    Card(modifier = modifier.fillMaxWidth().testTag(DID_YOU_KNOW_CARD_TEST_TAG)) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = FunFactIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                SectionTitle(stringResource(R.string.quiz_fact_title), Modifier.weight(1f))
                fact.sourceUrl?.let { url ->
                    IconButton(onClick = { openUrl(url) }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = stringResource(R.string.quiz_source),
                        )
                    }
                }
            }
            Text(
                text = format.format(fact.text),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
            if (uiState.hasMore) {
                TextButton(
                    onClick = onNextClick,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                    modifier = Modifier.testTag(DID_YOU_KNOW_NEXT_TEST_TAG),
                ) {
                    Text(stringResource(R.string.quiz_fact_another))
                }
            }
        }
    }
}

@Immutable
data class DidYouKnowUiState(
    val facts: List<FunFact> = emptyList(),
    val index: Int = 0,
) {
    val fact: FunFact? get() = facts.getOrNull(index)
    val hasMore: Boolean get() = facts.size > 1
}

@HiltViewModel
class DidYouKnowViewModel @Inject constructor(
    private val dailyFunFacts: DailyFunFactsUseCase,
) : ViewModel() {

    private val state = MutableStateFlow(DidYouKnowUiState())
    val uiState: StateFlow<DidYouKnowUiState> = state.asStateFlow()

    init {
        viewModelScope.launch { state.value = DidYouKnowUiState(facts = dailyFunFacts()) }
    }

    /** Steps through the day's facts, back to the first after the last. */
    fun onNextFact() {
        state.update { it.copy(index = if (it.facts.isEmpty()) 0 else (it.index + 1) % it.facts.size) }
    }
}
