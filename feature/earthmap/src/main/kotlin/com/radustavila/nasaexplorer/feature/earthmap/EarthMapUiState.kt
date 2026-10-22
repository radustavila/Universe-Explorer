package com.radustavila.nasaexplorer.feature.earthmap

import androidx.compose.runtime.Immutable
import com.radustavila.nasaexplorer.core.map.gibs.GibsImagery
import com.radustavila.nasaexplorer.core.map.gibs.GibsLayer
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The three looks the satellite map offers. */
enum class EarthMapMode {
    /** Daily true colour from orbit, for the selected day. */
    TrueColor,

    /** City lights at night (a fixed 2016 composite). */
    NightLights,

    /** Cloud-free relief and ocean floor. */
    BlueMarble,
}

@Immutable
data class EarthMapUiState(
    val mode: EarthMapMode,
    /** The day picked; the date label follows it at once. */
    val date: LocalDate,
    /** First selectable day of the slider. */
    val minDate: LocalDate,
    /** Yesterday: GIBS's daily mosaic for today is often still partial. */
    val maxDate: LocalDate,
    /** The day the map draws: [date] once the picker has rested for a moment. */
    val imageryDate: LocalDate = date,
    val isOffline: Boolean = false,
) {
    /** Only true colour changes with the day; the other two are single composites. */
    val isDateSelectable: Boolean get() = mode == EarthMapMode.TrueColor

    val dayCount: Int get() = ChronoUnit.DAYS.between(minDate, maxDate).toInt()

    /** True while the picked day waits to be drawn. */
    val isImageryPending: Boolean get() = isDateSelectable && imageryDate != date

    val imagery: GibsImagery
        get() = when (mode) {
            EarthMapMode.TrueColor -> GibsImagery.trueColor(imageryDate)
            EarthMapMode.NightLights -> GibsImagery(GibsLayer.ViirsBlackMarble)
            EarthMapMode.BlueMarble -> GibsImagery(GibsLayer.BlueMarble)
        }
}
