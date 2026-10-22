package com.radustavila.nasaexplorer.feature.news.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.designsystem.error.toUiError
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDataFreshnessUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveNewsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshNewsUseCase
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.NewsItem
import com.radustavila.nasaexplorer.core.model.NewsKind
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The News list. Items come from the cache only; opening the screen asks for a refresh, which
 * [RefreshNewsUseCase] skips while the feeds are fresh, and pull-to-refresh forces one. The chosen
 * kind survives rotation and process death.
 */
@HiltViewModel
class NewsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observeNews: ObserveNewsUseCase,
    private val refreshNews: RefreshNewsUseCase,
    observeDataFreshness: ObserveDataFreshnessUseCase,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val kind = savedStateHandle.getStateFlow<String?>(KEY_KIND, null)
    private val transient = MutableStateFlow(Transient())

    val uiState: StateFlow<NewsUiState> = combine(
        observeNews(),
        kind,
        transient,
        observeDataFreshness(*RefreshNewsUseCase.SOURCES),
    ) { items, kindName, transient, freshness ->
        val selected = NewsKind.entries.firstOrNull { it.name == kindName }
        NewsUiState(
            items = if (selected == null) items else items.filter { it.kind == selected },
            liveItem = items.firstOrNull(NewsItem::isLive),
            hasNews = items.isNotEmpty(),
            selectedKind = selected,
            isLoaded = true,
            isRefreshing = transient.isRefreshing,
            error = transient.error,
            freshness = freshness,
            now = timeProvider.now(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = NewsUiState(now = timeProvider.now()),
    )

    init {
        refresh(force = false)
    }

    fun onRefresh() = refresh(force = true)

    /** Shows only [kind]; choosing the shown kind again, or `null`, shows everything. */
    fun onKindSelected(kind: NewsKind?) {
        savedStateHandle[KEY_KIND] = kind?.takeIf { it != uiState.value.selectedKind }?.name
    }

    fun onErrorShown() {
        transient.update { it.copy(error = null) }
    }

    private fun refresh(force: Boolean) {
        if (transient.value.isRefreshing) return
        transient.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val result = refreshNews(force = force)
            transient.update {
                it.copy(isRefreshing = false, error = (result as? DataResult.Failure)?.error?.toUiError())
            }
        }
    }

    private data class Transient(val isRefreshing: Boolean = false, val error: UiError? = null)

    private companion object {
        const val KEY_KIND = "kind"
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}

data class NewsUiState(
    /** The cached items of [selectedKind] (all while it is `null`), newest first. */
    val items: List<NewsItem> = emptyList(),
    /** The live stream to show above the list, if the video feed has one. */
    val liveItem: NewsItem? = null,
    /** Whether anything is cached at all, whatever the filter. */
    val hasNews: Boolean = false,
    val selectedKind: NewsKind? = null,
    val isLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
    /** The last refresh failed for every feed. */
    val error: UiError? = null,
    val freshness: DataFreshness = DataFreshness(),
    /** What ages ("3 hours ago") are relative to. */
    val now: Instant = Instant.EPOCH,
)
