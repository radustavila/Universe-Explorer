package com.radustavila.nasaexplorer.feature.debug

import com.radustavila.nasaexplorer.core.domain.model.DebugInfo

data class DebugUiState(
    val info: DebugInfo? = null,
) {
    val isLoading: Boolean get() = info == null
}
