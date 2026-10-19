package com.radustavila.nasaexplorer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage

/**
 * Image loading with explicit loading, failure and "nothing to show" states.
 *
 * Missing and broken media are normal here: NASA's archive contains dead links, and it publishes
 * videos with no still frame at all (`thumbnail_url: ""`). Coil's default behaviour - an empty box -
 * would look like a rendering bug, so each case gets its own placeholder.
 *
 * [previewUrl] names a smaller variant of the same picture, typically one the feed has already
 * cached. It is drawn while [url] downloads, and kept if [url] fails, which turns the wait for a
 * multi-megabyte HD file into a sharpening image rather than a spinner over an empty box.
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

        val preview = previewUrl?.takeIf { it != url }

        SubcomposeAsyncImage(
            model = url,
            // Described by the frame above; describing the bitmap as well would announce it twice.
            contentDescription = null,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
            loading = {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (preview == null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        CachedPreview(url = preview, contentScale = contentScale)
                    }
                }
            },
            error = {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (preview == null) {
                        PlaceholderIcon(Icons.Outlined.BrokenImage)
                    } else {
                        CachedPreview(url = preview, contentScale = contentScale)
                    }
                }
            },
        )
    }
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
        modifier = Modifier.size(40.dp),
    )
}
