package com.radustavila.nasaexplorer.feature.debug

import com.radustavila.nasaexplorer.core.domain.model.DebugInfo

data class DebugUiState(
    val info: DebugInfo? = null,
    /** Comets fainter than the cut-off are shown on the sky map and in the sky events calendar. */
    val showFaintComets: Boolean = false,
) {
    val isLoading: Boolean get() = info == null
}
