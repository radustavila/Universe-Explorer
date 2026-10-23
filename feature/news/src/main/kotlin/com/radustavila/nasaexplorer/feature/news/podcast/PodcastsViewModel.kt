package com.radustavila.nasaexplorer.feature.news.podcast

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.designsystem.error.UiError
import com.radustavila.nasaexplorer.core.designsystem.error.toUiError
import com.radustavila.nasaexplorer.core.domain.podcast.PodcastPlayer
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDataFreshnessUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObservePodcastEpisodesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshPodcastsUseCase
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.PodcastEpisode
import com.radustavila.nasaexplorer.core.model.PodcastPlayback
import com.radustavila.nasaexplorer.core.model.PodcastShow
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
 * The Podcasts tab of the News screen. Episodes come from the cache only; showing the tab asks for
 * a refresh, which [RefreshPodcastsUseCase] skips while the feeds are fresh, and pull-to-refresh
 * forces one. The chosen show survives rotation and process death.
 */
@HiltViewModel
class PodcastsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observeEpisodes: ObservePodcastEpisodesUseCase,
    private val refreshPodcasts: RefreshPodcastsUseCase,
    observeDataFreshness: ObserveDataFreshnessUseCase,
    private val player: PodcastPlayer,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val show = savedStateHandle.getStateFlow<String?>(KEY_SHOW, null)
    private val transient = MutableStateFlow(Transient())

    val uiState: StateFlow<PodcastsUiState> = combine(
        observeEpisodes(),
        show,
        transient,
        observeDataFreshness(*RefreshPodcastsUseCase.SOURCES),
        player.playback,
    ) { episodes, showKey, transient, freshness, playback ->
        val selected = PodcastShow.fromKey(showKey.orEmpty())
        PodcastsUiState(
            episodes = if (selected == null) episodes else episodes.filter { it.show == selected },
            hasEpisodes = episodes.isNotEmpty(),
            selectedShow = selected,
            isLoaded = true,
            isRefreshing = transient.isRefreshing,
            error = transient.error,
            freshness = freshness,
            playback = playback,
            now = timeProvider.now(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = PodcastsUiState(now = timeProvider.now()),
    )

    init {
        refresh(force = false)
    }

    fun onRefresh() = refresh(force = true)

    /** Shows only [show]; choosing the shown one again, or `null`, shows every show. */
    fun onShowSelected(show: PodcastShow?) {
        savedStateHandle[KEY_SHOW] = show?.takeIf { it != uiState.value.selectedShow }?.key
    }

    /** Plays the episode, or pauses it when it is the one playing. */
    fun onPlayClick(episode: PodcastEpisode) {
        val playback = player.playback.value
        if (playback.episode?.id == episode.id && playback.isPlaying) player.pause() else player.play(episode)
    }

    fun onErrorShown() {
        transient.update { it.copy(error = null) }
    }

    private fun refresh(force: Boolean) {
        if (transient.value.isRefreshing) return
        transient.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val result = refreshPodcasts(force = force)
            transient.update {
                it.copy(isRefreshing = false, error = (result as? DataResult.Failure)?.error?.toUiError())
            }
        }
    }

    private data class Transient(val isRefreshing: Boolean = false, val error: UiError? = null)

    private companion object {
        const val KEY_SHOW = "podcast_show"
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}

data class PodcastsUiState(
    /** The cached episodes of [selectedShow] (every show while it is `null`), newest first. */
    val episodes: List<PodcastEpisode> = emptyList(),
    /** Whether anything is cached at all, whatever the filter. */
    val hasEpisodes: Boolean = false,
    val selectedShow: PodcastShow? = null,
    val isLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
    /** The last refresh failed for every show. */
    val error: UiError? = null,
    val freshness: DataFreshness = DataFreshness(),
    val playback: PodcastPlayback = PodcastPlayback(),
    /** What ages ("3 days ago") are relative to. */
    val now: Instant = Instant.EPOCH,
) {
    fun isPlaying(episode: PodcastEpisode): Boolean = playback.isPlaying && playback.episode?.id == episode.id

    /** Where the player is in [episode], while it is loaded. */
    fun positionIn(episode: PodcastEpisode) = playback.position.takeIf { playback.episode?.id == episode.id }
}
