package com.radustavila.nasaexplorer.feature.moonmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.domain.moon.MoonTerminator
import com.radustavila.nasaexplorer.core.domain.usecase.DownloadMoonTilesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveLunarLandingSitesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveMoonIlluminationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveMoonTilesPackUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RemoveMoonTilesUseCase
import com.radustavila.nasaexplorer.core.model.LunarLandingSite
import com.radustavila.nasaexplorer.core.model.LunarProgramme
import com.radustavila.nasaexplorer.core.model.MoonIllumination
import com.radustavila.nasaexplorer.core.model.MoonTilesPack
import com.radustavila.nasaexplorer.core.model.SelenographicPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the Moon map shows; [sites] are those of the [shownProgrammes] only. */
data class MoonMapUiState(
    val isLoading: Boolean = true,
    /** Every programme in the table, oldest first: one filter chip each. */
    val programmes: List<LunarProgramme> = emptyList(),
    val shownProgrammes: Set<LunarProgramme> = emptySet(),
    val sites: List<LunarLandingSite> = emptyList(),
    val selectedSite: LunarLandingSite? = null,
    val illumination: MoonIllumination? = null,
    /** The night side as polygons, from [illumination]. */
    val nightSide: List<List<SelenographicPoint>> = emptyList(),
    /** Null until the store answered. */
    val tiles: MoonTilesPack? = null,
)

@HiltViewModel
class MoonMapViewModel @Inject constructor(
    observeSites: ObserveLunarLandingSitesUseCase,
    observeIllumination: ObserveMoonIlluminationUseCase,
    observeTiles: ObserveMoonTilesPackUseCase,
    private val downloadTiles: DownloadMoonTilesUseCase,
    private val removeTiles: RemoveMoonTilesUseCase,
) : ViewModel() {

    /** Programmes the user switched off; empty shows every site. */
    private val hiddenProgrammes = MutableStateFlow<Set<LunarProgramme>>(emptySet())
    private val selectedSiteId = MutableStateFlow<String?>(null)

    private val night = observeIllumination()
        .map<MoonIllumination, Night?> { Night(it, MoonTerminator.nightSide(it.subsolarPoint)) }
        .onStart { emit(null) }

    private val tiles = observeTiles()
        .catch { emit(MoonTilesPack.Unavailable) }
        .map<MoonTilesPack, MoonTilesPack?> { it }
        .onStart { emit(null) }

    val uiState: StateFlow<MoonMapUiState> = combine(
        observeSites(),
        night,
        tiles,
        hiddenProgrammes,
        selectedSiteId,
    ) { sites, night, tiles, hidden, selectedId ->
        val programmes = sites.map(LunarLandingSite::programme).distinct()
        MoonMapUiState(
            isLoading = false,
            programmes = programmes,
            shownProgrammes = programmes.toSet() - hidden,
            sites = sites.filter { it.programme !in hidden },
            selectedSite = sites.firstOrNull { it.id == selectedId },
            illumination = night?.illumination,
            nightSide = night?.polygons.orEmpty(),
            tiles = tiles,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MoonMapUiState())

    fun onProgrammeToggled(programme: LunarProgramme) {
        hiddenProgrammes.update { hidden -> if (programme in hidden) hidden - programme else hidden + programme }
    }

    fun onSiteSelected(siteId: String) {
        selectedSiteId.value = siteId
    }

    fun onSiteDismissed() {
        selectedSiteId.value = null
    }

    fun onDownloadTiles() {
        viewModelScope.launch { downloadTiles() }
    }

    fun onRemoveTiles() {
        viewModelScope.launch { removeTiles() }
    }

    private data class Night(val illumination: MoonIllumination, val polygons: List<List<SelenographicPoint>>)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
