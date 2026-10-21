package com.radustavila.nasaexplorer.core.designsystem.adaptive

import androidx.compose.runtime.Composable

/**
 * Lays out a feature's list screen, alone or next to a detail pane.
 *
 * A feature's list destination takes one and calls it with the click handler that opens an item
 * full screen (`openFullScreen`) and with its list, which it must give the click handler it is
 * handed back. `:app` supplies a host that shows the item in a detail pane when the window has
 * room for two panes and keeps `openFullScreen` otherwise, so features stay unaware of each
 * other's screens and of the window size.
 */
typealias ListDetailHost<T> = @Composable (
    openFullScreen: (T) -> Unit,
    list: @Composable (onItemClick: (T) -> Unit) -> Unit,
) -> Unit

/** The list alone: every item opens full screen. The default until `:app` supplies a host. */
fun <T> listOnly(): ListDetailHost<T> = { openFullScreen, list -> list(openFullScreen) }
