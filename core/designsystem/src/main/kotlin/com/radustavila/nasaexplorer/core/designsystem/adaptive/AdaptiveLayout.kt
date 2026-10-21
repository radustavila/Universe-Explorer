package com.radustavila.nasaexplorer.core.designsystem.adaptive

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Shared widths that keep screens from stretching on tablets, foldables and phones in landscape.
 * Grids take a minimum column width (GridCells.Adaptive), so a phone held upright keeps its single
 * column and wider windows gain columns; rows of text stop growing at a readable width.
 */

/** Narrowest column of image cards (APOD, favourites): one column on a phone, two from a small tablet. */
val CardColumnMinWidth: Dp = 300.dp

/** Narrowest column of content cards that carry text and controls (Sky tonight, hub entries). */
val PanelColumnMinWidth: Dp = 360.dp

/** Widest a column of list rows or running text grows before it is centred instead. */
val ReadableMaxWidth: Dp = 640.dp

/**
 * Side padding that centres content at most [maxWidth] wide in a container [containerWidth] wide,
 * and never less than [minPadding]. For lists that should still scroll from anywhere across the
 * window, which a narrower list would not.
 */
fun centeringPadding(containerWidth: Dp, maxWidth: Dp = ReadableMaxWidth, minPadding: Dp = 0.dp): Dp =
    maxOf(minPadding, (containerWidth - maxWidth) / 2)
