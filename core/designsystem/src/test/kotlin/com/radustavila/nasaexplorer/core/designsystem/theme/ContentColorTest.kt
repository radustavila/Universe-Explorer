package com.radustavila.nasaexplorer.core.designsystem.theme

import androidx.compose.material3.Card
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Text on every surface reads at 4.5:1 or better (WCAG 1.4.3), and cards draw their text in the
 * full-strength `onSurface`, keeping `onSurfaceVariant` for what a screen marks as secondary.
 */
@RunWith(RobolectricTestRunner::class)
class ContentColorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val surfaces = mapOf(
        "background" to SpaceColors.background,
        "surfaceVariant" to SpaceColors.surfaceVariant,
        "surfaceContainer" to SpaceColors.surfaceContainer,
        "surfaceContainerHigh" to SpaceColors.surfaceContainerHigh,
        "surfaceContainerHighest" to SpaceColors.surfaceContainerHighest,
    )

    @Test
    fun `body and secondary text reach 4,5 to 1 on every surface`() {
        surfaces.forEach { (name, surface) ->
            listOf("onSurface" to SpaceColors.onSurface, "onSurfaceVariant" to SpaceColors.onSurfaceVariant)
                .forEach { (textName, text) ->
                    val ratio = contrast(text, surface)
                    assertTrue("$textName on $name is only %.2f:1".format(ratio), ratio >= BODY_TEXT_RATIO)
                }
        }
    }

    @Test
    fun `card containers resolve to onSurface, surfaceVariant to onSurfaceVariant`() {
        assertEquals(SpaceColors.onSurface, SpaceColors.contentColorFor(SpaceColors.surfaceContainerHighest))
        assertEquals(SpaceColors.onSurface, SpaceColors.contentColorFor(SpaceColors.surfaceContainerHigh))
        assertEquals(SpaceColors.onSurfaceVariant, SpaceColors.contentColorFor(SpaceColors.surfaceVariant))
    }

    @Test
    fun `text in a default card is drawn in onSurface`() {
        var cardContentColor = Color.Unspecified
        var onSurface = Color.Unspecified
        composeTestRule.setContent {
            NasaTheme {
                onSurface = MaterialTheme.colorScheme.onSurface
                Card { cardContentColor = LocalContentColor.current }
            }
        }

        composeTestRule.runOnIdle { assertEquals(onSurface, cardContentColor) }
    }

    private fun contrast(a: Color, b: Color): Float {
        val lighter = maxOf(a.luminance(), b.luminance())
        val darker = minOf(a.luminance(), b.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private companion object {
        const val BODY_TEXT_RATIO = 4.5f
    }
}
