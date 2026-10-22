package com.radustavila.nasaexplorer.feature.news.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveNewsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshNewsUseCase
import com.radustavila.nasaexplorer.core.model.NewsItem
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Today card reads the news cache. Showing it asks for a refresh, which [RefreshNewsUseCase]
 * skips while the feeds are fresh: news has no background job, so this is what keeps it current for
 * someone who keeps the card on Today.
 */
@HiltViewModel
class LatestFromNasaViewModel @Inject constructor(
    observeNews: ObserveNewsUseCase,
    refreshNews: RefreshNewsUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    val uiState: StateFlow<LatestFromNasaUiState> = observeNews()
        .map { items ->
            LatestFromNasaUiState(
                live = items.firstOrNull(NewsItem::isLive),
                latest = items.filterNot(NewsItem::isLive).take(LATEST_COUNT),
                now = timeProvider.now(),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
            initialValue = LatestFromNasaUiState(),
        )

    init {
        viewModelScope.launch { refreshNews() }
    }

    private companion object {
        const val LATEST_COUNT = 3
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}

data class LatestFromNasaUiState(
    /** A live stream, shown first. */
    val live: NewsItem? = null,
    /** The newest items that are not live, newest first. */
    val latest: List<NewsItem> = emptyList(),
    val now: Instant = Instant.EPOCH,
)
