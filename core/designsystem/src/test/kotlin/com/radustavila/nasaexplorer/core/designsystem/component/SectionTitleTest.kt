package com.radustavila.nasaexplorer.core.designsystem.component

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SectionTitleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `a section title is a heading for TalkBack`() {
        composeTestRule.setContent { SectionTitle("The Sun") }

        composeTestRule.onNodeWithText("The Sun").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }
}
