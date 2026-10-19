package com.radustavila.nasaexplorer.core.map.gibs

import androidx.compose.runtime.Immutable
import java.time.LocalDate

/**
 * What [com.radustavila.nasaexplorer.core.map.SpaceMap] draws above its Blue Marble base: a GIBS
 * layer on a given day. The date is ignored by layers that are not daily.
 */
@Immutable
data class GibsImagery(val layer: GibsLayer, val date: LocalDate? = null) {
    val tileUrlTemplate: String get() = layer.tileUrlTemplate(date)
}
