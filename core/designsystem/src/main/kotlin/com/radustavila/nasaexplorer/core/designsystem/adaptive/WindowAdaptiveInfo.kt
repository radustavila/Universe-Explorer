package com.radustavila.nasaexplorer.core.designsystem.adaptive

import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.runtime.compositionLocalOf
import androidx.window.core.layout.WindowSizeClass

/**
 * The window's size classes and posture, computed once by `:app` at the root of the UI and
 * provided here so features can adapt without depending on `:app` or measuring the window again.
 *
 * Without a provider (previews, a feature's own UI tests) it describes a phone held upright.
 */
val LocalWindowAdaptiveInfo = compositionLocalOf {
    WindowAdaptiveInfo(
        windowSizeClass = WindowSizeClass(minWidthDp = 0, minHeightDp = WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND),
        windowPosture = Posture(),
    )
}

/** Shorter than a phone held upright: a phone in landscape, or a small multi-window split. */
val WindowAdaptiveInfo.isHeightCompact: Boolean
    get() = !windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

/** Narrower than a small tablet: a phone held upright. */
val WindowAdaptiveInfo.isWidthCompact: Boolean
    get() = !windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
