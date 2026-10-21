package com.radustavila.nasaexplorer.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.radustavila.nasaexplorer.core.designsystem.R

/**
 * Full-screen, pinch-to-zoom image viewer: drags to pan, double-taps to zoom, and draws [url] on
 * top of an already cached [previewUrl] while it downloads.
 *
 * The one full-size viewer of the app: every feature that opens a picture full size uses it.
 */
@Composable
fun ZoomableImageDialog(
    url: String,
    previewUrl: String?,
    contentDescription: String?,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        // Edge to edge: a full-screen picture should not be framed by the screen behind it
        // showing through the system bars.
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            ZoomableImage(
                url = url,
                previewUrl = previewUrl,
                contentDescription = contentDescription,
            )

            FilledIconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(48.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.16f),
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.action_close_full_size),
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(
    url: String,
    previewUrl: String?,
    contentDescription: String?,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    // Panning is only meaningful once the image is larger than the viewport, and it must stop at
    // the edges of the image rather than letting it drift off screen.
    fun clamp(candidate: Offset, atScale: Float): Offset {
        if (atScale <= 1f) return Offset.Zero
        val maxX = viewport.width * (atScale - 1f) / 2f
        val maxY = viewport.height * (atScale - 1f) / 2f
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY),
        )
    }

    val transformState = rememberTransformableState { centroid, zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(MIN_SCALE, MAX_SCALE)
        // Zoom around the pinch centroid rather than the middle of the screen, so the detail the
        // fingers are on stays under them. The layer scales about its own centre, so the point is
        // held by moving the offset by the same ratio the scale changed.
        val fromCentre = centroid - Offset(viewport.width / 2f, viewport.height / 2f)
        val zoomed = fromCentre - (fromCentre - offset) * (newScale / scale)
        offset = clamp(zoomed + panChange, newScale)
        scale = newScale
    }

    // Double tap is the expected shortcut in a photo viewer, and animating the scale makes clear
    // that the same gesture undoes itself.
    val animatedScale = animateFloatAsState(targetValue = scale, label = "zoom")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size -> viewport = size }
            .transformable(state = transformState)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else DOUBLE_TAP_SCALE
                        offset = clamp(offset, scale)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        NasaAsyncImage(
            url = url,
            previewUrl = previewUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            backgroundColor = Color.Transparent,
            modifier = Modifier
                .fillMaxSize()
                // Lambda form: the zoom animation and pans update the layer only, with no
                // recomposition or relayout per frame.
                .graphicsLayer {
                    scaleX = animatedScale.value
                    scaleY = animatedScale.value
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f
private const val DOUBLE_TAP_SCALE = 2.5f
