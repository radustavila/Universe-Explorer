package com.radustavila.nasaexplorer.feature.dsn

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDataFreshnessUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDsnSnapshotUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveFarthestProbesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.PollDeepSpaceNetworkUseCase
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.DeepSpaceProbe
import com.radustavila.nasaexplorer.core.model.DsnSiteLink
import com.radustavila.nasaexplorer.core.model.DsnSnapshot
import com.radustavila.nasaexplorer.core.model.SyncSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@Immutable
data class DsnUiState(
    /** False until the cache has been read once. */
    val isLoaded: Boolean = false,
    /** The last good snapshot; null before the first successful poll. */
    val snapshot: DsnSnapshot? = null,
    val farthest: List<DeepSpaceProbe> = emptyList(),
    val freshness: DataFreshness = DataFreshness(),
    /** The spacecraft whose details are open, followed through every new snapshot. */
    val selected: DsnSiteLink? = null,
) {
    /** Horizons ids of the spacecraft with a signal on, to mark them in the farthest list. */
    val talkingNaifIds: Set<Int>
        get() = snapshot?.activeLinks?.mapNotNull { it.link.spacecraft.naifId }?.toSet().orEmpty()
}

/**
 * The Deep Space Network screen. Reads only the cache; [pollWhileVisible] is what keeps it live,
 * and the screen runs it only while it is started (see `DsnScreen`), so nothing polls in the
 * background or behind another screen.
 */
@HiltViewModel
class DsnViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    observeSnapshot: ObserveDsnSnapshotUseCase,
    observeFarthest: ObserveFarthestProbesUseCase,
    observeFreshness: ObserveDataFreshnessUseCase,
    private val poll: PollDeepSpaceNetworkUseCase,
) : ViewModel() {

    /** "DSS43/VGR2": the dish and spacecraft, so an array of dishes on one craft opens the one tapped. */
    private val selectedKey = savedStateHandle.getStateFlow<String?>(KEY_SELECTED, null)

    val uiState: StateFlow<DsnUiState> = combine(
        observeSnapshot(),
        observeFarthest(),
        observeFreshness(SyncSource.DSN_NOW),
        selectedKey,
    ) { snapshot, farthest, freshness, key ->
        DsnUiState(
            isLoaded = true,
            snapshot = snapshot,
            farthest = farthest,
            freshness = freshness,
            selected = key?.let { snapshot?.findLink(it) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS),
        initialValue = DsnUiState(),
    )

    /** Polls the live feed until cancelled; call from a lifecycle-bound coroutine. */
    suspend fun pollWhileVisible(): Nothing = poll()

    fun onSpacecraftClick(dishName: String, spacecraftCode: String) {
        savedStateHandle[KEY_SELECTED] = "$dishName$KEY_SEPARATOR$spacecraftCode"
    }

    fun onSheetDismiss() {
        savedStateHandle[KEY_SELECTED] = null
    }

    private fun DsnSnapshot.findLink(key: String): DsnSiteLink? {
        val (dishName, code) = key.split(KEY_SEPARATOR).takeIf { it.size == 2 } ?: return null
        return sites.firstNotNullOfOrNull { site ->
            site.dishes.firstOrNull { it.name == dishName }?.let { dish ->
                dish.links.firstOrNull { it.spacecraft.code == code }?.let { DsnSiteLink(site, dish, it) }
            }
        }
    }

    private companion object {
        const val KEY_SELECTED = "selected_link"
        const val KEY_SEPARATOR = "/"
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
