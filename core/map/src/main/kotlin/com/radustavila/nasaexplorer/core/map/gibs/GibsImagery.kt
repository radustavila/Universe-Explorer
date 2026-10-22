package com.radustavila.nasaexplorer.core.map.gibs

import androidx.compose.runtime.Immutable
import java.time.LocalDate

/**
 * What [com.radustavila.nasaexplorer.core.map.SpaceMap] draws above its Blue Marble base: a GIBS
 * layer on a given day. The date is ignored by layers that are not daily.
 *
 * [gapFill] is drawn under [layer] to cover its no-data areas, e.g. a second satellite's pass for
 * the swath gaps of a daily mosaic. Whatever neither covers shows the base.
 */
@Immutable
data class GibsImagery(val layer: GibsLayer, val date: LocalDate? = null, val gapFill: GibsLayer? = null) {
    val tileUrlTemplate: String get() = layer.tileUrlTemplate(date)

    /** The layers to draw, bottom to top. */
    val layers: List<GibsLayer> get() = listOfNotNull(gapFill, layer)

    companion object {
        /** True colour for [date], with the other satellite of that day filling the swath gaps. */
        fun trueColor(date: LocalDate): GibsImagery =
            GibsImagery(GibsLayer.trueColorFor(date), date, gapFill = GibsLayer.trueColorGapFillFor(date))
    }
}
