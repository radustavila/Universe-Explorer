package com.radustavila.nasaexplorer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Image loading with explicit loading, failure and "nothing to show" states.
 *
 * Missing and broken media are normal here: NASA's archive contains dead links, and it publishes
 * videos with no still frame at all (`thumbnail_url: ""`). Coil's default behaviour - an empty box -
 * would look like a rendering bug, so each case gets its own placeholder.
 *
 * Without [previewUrl] (list cells, cards) the image is a single [AsyncImage]: the frame's
 * [backgroundColor] stands in while it loads and a broken-image icon replaces it if it fails, all
 * drawn by painters, so a scrolling list does no subcomposition per cell.
 *
 * [previewUrl] names a smaller variant of the same picture, typically one the feed has already
 * cached. It is drawn while [url] downloads, and kept if [url] fails, which turns the wait for a
 * multi-megabyte HD file into a sharpening image rather than a spinner over an empty box. Only the
 * details and zoom screens pass it; the layered loading it needs is too heavy for list cells.
 *
 * [crossfade] fades the image in once loaded. Turn it off where images replace each other quickly,
 * such as animation frames, so each one appears at once.
 *
 * [contentDescription] belongs to the frame rather than to the loaded bitmap, so a screen reader
 * announces the picture while it is still loading or when it failed to load, too.
 */
@Composable
fun NasaAsyncImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackIcon: ImageVector = Icons.Outlined.BrokenImage,
    previewUrl: String? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    crossfade: Boolean = true,
) {
    Box(
        modifier = modifier
            .background(backgroundColor)
            .then(
                if (contentDescription == null) {
                    Modifier
                } else {
                    Modifier.semantics {
                        this.contentDescription = contentDescription
                        role = Role.Image
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null) {
            PlaceholderIcon(fallbackIcon)
            return@Box
        }

        val model = rememberImageModel(url, crossfade)
        val preview = previewUrl?.takeIf { it != url }
        if (preview == null) {
            AsyncImage(
                model = model,
                // Described by the frame above; describing the bitmap as well would announce it twice.
                contentDescription = null,
                contentScale = contentScale,
                error = rememberCenteredIconPainter(Icons.Outlined.BrokenImage),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LayeredImage(model = model, preview = preview, contentScale = contentScale)
        }
    }
}

/** [url] as is, or wrapped in a request that skips the loader's default crossfade. */
@Composable
private fun rememberImageModel(url: String, crossfade: Boolean): Any {
    if (crossfade) return url
    val context = LocalPlatformContext.current
    return remember(context, url) { ImageRequest.Builder(context).data(url).crossfade(false).build() }
}

/** The HD image over its cached [preview], which shows while the HD file loads or if it fails. */
@Composable
private fun LayeredImage(model: Any, preview: String, contentScale: ContentScale) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = null,
        contentScale = contentScale,
        modifier = Modifier.fillMaxSize(),
        loading = { CachedPreview(url = preview, contentScale = contentScale) },
        error = { CachedPreview(url = preview, contentScale = contentScale) },
    )
}

/**
 * The already-cached, lower resolution copy shown underneath a download in progress. It never
 * reports its own loading or failure: it is itself the fallback, so an empty box is all there is.
 */
@Composable
private fun CachedPreview(url: String, contentScale: ContentScale) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = contentScale,
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * Draws [icon] at the placeholder size in the middle of whatever box it is given. Its intrinsic
 * size is unspecified, so the image's content scale cannot stretch it to fill the frame.
 */
@Composable
private fun rememberCenteredIconPainter(icon: ImageVector): Painter {
    val vector = rememberVectorPainter(icon)
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    val iconSize = with(LocalDensity.current) { PLACEHOLDER_ICON_SIZE.toPx() }
    return remember(vector, tint, iconSize) { CenteredIconPainter(vector, iconSize, tint) }
}

private class CenteredIconPainter(
    private val icon: Painter,
    private val iconSize: Float,
    tint: Color,
) : Painter() {
    private val colorFilter = ColorFilter.tint(tint)

    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        translate(left = (size.width - iconSize) / 2f, top = (size.height - iconSize) / 2f) {
            with(icon) { draw(Size(iconSize, iconSize), colorFilter = colorFilter) }
        }
    }
}

/** Placeholder for a video NASA published without a still frame. */
@Composable
fun VideoPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        PlaceholderIcon(Icons.Outlined.PlayCircle)
    }
}

@Composable
private fun PlaceholderIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(PLACEHOLDER_ICON_SIZE),
    )
}

private val PLACEHOLDER_ICON_SIZE = 40.dp
