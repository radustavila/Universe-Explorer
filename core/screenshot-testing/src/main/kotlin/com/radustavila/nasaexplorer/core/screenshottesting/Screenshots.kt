package com.radustavila.nasaexplorer.core.screenshottesting

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.radustavila.nasaexplorer.core.designsystem.adaptive.LocalWindowAdaptiveInfo
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import java.util.TimeZone

/**
 * Renders [content] full window in the app theme and captures it as `src/test/screenshots/<name>.png`.
 *
 * The test class runs on Robolectric with `@GraphicsMode(NATIVE)`; its `@Config(qualifiers)` (see
 * [ScreenshotDevices]) sets the window, which reaches the content through `LocalWindowAdaptiveInfo`
 * as in the app. Images come from [installFakeImageLoader] and clocks read UTC, so the result does
 * not depend on the network or the machine's time zone.
 */
fun ComposeContentTestRule.captureScreen(name: String, content: @Composable () -> Unit) {
    prepare()
    setContent {
        ScreenshotTheme {
            Surface(color = MaterialTheme.colorScheme.background, content = content)
        }
    }
    onRoot().captureRoboImage(goldenPath(name), ScreenshotOptions)
}

/**
 * Like [captureScreen], for a component laid out [width] wide on the app background; only the
 * component and its padding end up in the golden. Render it in a [ScreenshotDevices.COMPACT] window.
 */
fun ComposeContentTestRule.captureComponent(
    name: String,
    width: Dp = COMPONENT_WIDTH,
    content: @Composable () -> Unit,
) {
    prepare()
    setContent {
        ScreenshotTheme {
            // A surface, as on screen, so text takes the theme's content colour.
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.testTag(COMPONENT_TEST_TAG)) {
                Box(Modifier.padding(COMPONENT_PADDING).width(width)) {
                    content()
                }
            }
        }
    }
    onNodeWithTag(COMPONENT_TEST_TAG).captureRoboImage(goldenPath(name), ScreenshotOptions)
}

@Composable
private fun ScreenshotTheme(content: @Composable () -> Unit) {
    NasaTheme {
        CompositionLocalProvider(LocalWindowAdaptiveInfo provides currentWindowAdaptiveInfoV2(), content = content)
    }
}

private fun prepare() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    installFakeImageLoader()
}

private fun goldenPath(name: String) = "src/test/screenshots/$name.png"

private val ScreenshotOptions = RoborazziOptions(
    // Half of xhdpi keeps the goldens small and smooths over sub-pixel text differences.
    recordOptions = RoborazziOptions.RecordOptions(resizeScale = 0.5),
    // Tolerates the anti-aliasing differences between the Robolectric renderers on macOS and Linux.
    compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
)

private const val COMPONENT_TEST_TAG = "screenshot-component"
private val COMPONENT_WIDTH = 360.dp
private val COMPONENT_PADDING = 16.dp
