package com.radustavila.nasaexplorer.core.designsystem.motion

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReducedMotionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `motion is on by default`() {
        setContent()

        composeTestRule.onNodeWithText("full motion").assertExists()
    }

    @Test
    fun `removing animations in the system settings reduces motion`() {
        setAnimatorScale(0f)
        setContent()

        composeTestRule.onNodeWithText("reduced motion").assertExists()
    }

    @Test
    fun `a slower but non-zero animator scale is not reduced motion`() {
        setAnimatorScale(0.5f)
        setContent()

        composeTestRule.onNodeWithText("full motion").assertExists()
    }

    @Test
    fun `follows the setting while the screen is shown`() {
        setContent()
        composeTestRule.onNodeWithText("full motion").assertExists()

        setAnimatorScale(0f)
        context.contentResolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)

        composeTestRule.onNodeWithText("reduced motion").assertExists()
    }

    private fun setAnimatorScale(scale: Float) {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun setContent() {
        composeTestRule.setContent {
            Text(if (rememberReducedMotion()) "reduced motion" else "full motion")
        }
    }
}
