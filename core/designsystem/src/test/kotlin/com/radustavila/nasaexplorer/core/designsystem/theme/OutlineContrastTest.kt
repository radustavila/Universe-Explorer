package com.radustavila.nasaexplorer.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG 1.4.11: borders that identify a control need 3:1 against what is around them. */
class OutlineContrastTest {

    private val surfaces = mapOf(
        "background" to SpaceColors.background,
        "surfaceContainer" to SpaceColors.surfaceContainer,
        "surfaceContainerHigh" to SpaceColors.surfaceContainerHigh,
        "surfaceVariant" to SpaceColors.surfaceVariant,
    )

    @Test
    fun `outline reaches 3 to 1 on every surface`() {
        assertContrast("outline", SpaceColors.outline)
    }

    @Test
    fun `outline variant, which draws chip borders, reaches 3 to 1 on every surface`() {
        assertContrast("outlineVariant", SpaceColors.outlineVariant)
    }

    private fun assertContrast(name: String, border: Color) {
        surfaces.forEach { (surfaceName, surface) ->
            val ratio = contrast(border, surface)
            assertTrue("$name on $surfaceName is only %.2f:1".format(ratio), ratio >= MIN_RATIO)
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val lighter = maxOf(a.luminance(), b.luminance())
        val darker = minOf(a.luminance(), b.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private companion object {
        const val MIN_RATIO = 3f
    }
}
