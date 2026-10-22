package com.radustavila.nasaexplorer.feature.skymap

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.radustavila.nasaexplorer.core.model.SkyMapBodies
import com.radustavila.nasaexplorer.core.skymap.HorizontalSky
import com.radustavila.nasaexplorer.core.skymap.SkyProjection
import com.radustavila.nasaexplorer.feature.skymap.render.SkyFrame
import com.radustavila.nasaexplorer.feature.skymap.render.SkyHitTest
import com.radustavila.nasaexplorer.feature.skymap.render.SkyNames
import com.radustavila.nasaexplorer.feature.skymap.render.SkyPalette
import com.radustavila.nasaexplorer.feature.skymap.render.SkyRenderer
import com.radustavila.nasaexplorer.feature.skymap.render.SkyTextStyles

const val SKY_MAP_CANVAS_TEST_TAG = "sky-map-canvas"

/**
 * The map itself. Where it looks is read only while drawing, so the 50 Hz sensor and drag updates
 * redraw the canvas without recomposing anything.
 */
@Composable
internal fun SkyMapCanvas(
    sky: HorizontalSky,
    bodies: SkyMapBodies?,
    camera: SkyCameraState,
    selected: SkyTarget?,
    palette: SkyPalette,
    names: SkyNames,
    description: String,
    onTap: (SkyTarget?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer(cacheSize = 0)
    val renderer = remember(textMeasurer) { SkyRenderer(textMeasurer, labelStyles()) }
    val tapRadius = with(LocalDensity.current) { TAP_RADIUS.toPx() }
    val currentSky = rememberUpdatedState(sky)
    val currentBodies = rememberUpdatedState(bodies)
    val currentOnTap = rememberUpdatedState(onTap)
    Spacer(
        modifier
            .testTag(SKY_MAP_CANVAS_TEST_TAG)
            .semantics { contentDescription = description }
            .pointerInput(camera) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val projection = camera.projection(size.width, size.height)
                    if (pan.x != 0f || pan.y != 0f) {
                        camera.drag(projection, centroid.x - pan.x, centroid.y - pan.y, centroid.x, centroid.y)
                    }
                    if (zoom != 1f) camera.zoom(zoom)
                }
            }
            .pointerInput(camera) {
                detectTapGestures { offset ->
                    val projection = camera.projection(size.width, size.height)
                    val target = SkyHitTest.find(
                        x = offset.x,
                        y = offset.y,
                        radius = tapRadius,
                        sky = currentSky.value,
                        bodies = currentBodies.value,
                        projection = projection,
                    )
                    currentOnTap.value(target)
                }
            }
            .drawBehind {
                renderer.draw(
                    this,
                    SkyFrame(
                        sky = sky,
                        bodies = bodies,
                        view = camera.view,
                        fieldOfViewDegrees = camera.fieldOfViewDegrees,
                        selected = selected,
                        palette = palette,
                        names = names,
                    ),
                )
            },
    )
}

private val TAP_RADIUS = 28.dp

private fun SkyCameraState.projection(width: Int, height: Int) =
    SkyProjection(view, width.toFloat(), height.toFloat(), fieldOfViewDegrees)

private fun labelStyles() = SkyTextStyles(
    star = TextStyle(fontSize = 11.sp),
    constellation = TextStyle(fontSize = 11.sp, letterSpacing = 1.5.sp),
    body = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    cardinal = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold),
)
