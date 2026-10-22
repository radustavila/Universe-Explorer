package com.radustavila.nasaexplorer.core.screenshottesting

/**
 * Windows the screenshot tests render in, as Robolectric qualifiers for `@Config(qualifiers = …)`.
 * Rendered at xhdpi and stored at half size, so a golden has as many pixels as the window has dp.
 */
object ScreenshotDevices {
    /** A phone held upright: bottom navigation bar, one column. */
    const val COMPACT = "w411dp-h891dp-xhdpi"

    /** A tablet in landscape: navigation rail, multi-column grids. */
    const val EXPANDED = "w1280dp-h800dp-xhdpi"
}
