package com.radustavila.nasaexplorer.feature.climate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.radustavila.nasaexplorer.core.designsystem.units.LocalUnitPreferences
import com.radustavila.nasaexplorer.core.model.ClimateIndicator
import com.radustavila.nasaexplorer.core.model.DataFreshness
import com.radustavila.nasaexplorer.core.model.MeasurementSystem
import com.radustavila.nasaexplorer.core.model.UnitPreferences
import com.radustavila.nasaexplorer.core.testing.data.TestClimate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClimateScreensTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val allSigns = VitalSignsUiState(isLoaded = true, series = TestClimate.all())

    @Test
    fun `each card shows its latest value, change and date, and opens its chart`() {
        val opened = mutableListOf<ClimateIndicator>()
        composeTestRule.setContent {
            Metric { VitalSignsContent(allSigns, onOpen = { opened += it }) }
        }

        composeTestRule.onNodeWithText("426.07 ppm").assertExists()
        composeTestRule.onNodeWithText("+1.38 °C").assertExists()
        composeTestRule.onNodeWithText("+84 mm").assertExists()
        composeTestRule.onNodeWithText("4.81 million km²").assertExists()
        composeTestRule.onNodeWithText("Latest value: Feb 2025").assertExists()
        composeTestRule.onNodeWithTag(vitalSignCardTag(ClimateIndicator.ARCTIC_SEA_ICE)).performClick()

        assertEquals(listOf(ClimateIndicator.ARCTIC_SEA_ICE), opened)
    }

    @Test
    fun `imperial units show °F, inches and square miles`() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalUnitPreferences provides UnitPreferences(MeasurementSystem.IMPERIAL)) {
                VitalSignsContent(allSigns, onOpen = {})
            }
        }

        composeTestRule.onNodeWithText("+2.48 °F").assertExists()
        composeTestRule.onNodeWithText("+3.3 in").assertExists()
        composeTestRule.onNodeWithText("1.86 million mi²").assertExists()
        composeTestRule.onNodeWithText("426.07 ppm").assertExists()
    }

    @Test
    fun `before the first download the section says why it is empty`() {
        composeTestRule.setContent {
            VitalSignsContent(VitalSignsUiState(isLoaded = true, freshness = OFFLINE), onOpen = {})
        }

        composeTestRule.onNodeWithText("The climate records download the first time you are online.").assertExists()
    }

    @Test
    fun `the detail credits its source in full and opens it`() {
        val opened = mutableListOf<String>()
        composeTestRule.setContent {
            Metric {
                ClimateContent(
                    uiState = ClimateUiState(
                        ClimateIndicator.ARCTIC_SEA_ICE,
                        isLoaded = true,
                        series = TestClimate.arcticSeaIce(),
                    ),
                    onBackClick = {},
                    onRefresh = {},
                    onOpenSource = { opened += it },
                )
            }
        }

        composeTestRule.onNodeWithText("https://doi.org/10.7265/a98x-0f50", substring = true)
            .performScrollTo()
            .assertExists()
        composeTestRule.onNodeWithText("Open the data source").performScrollTo().performClick()

        assertEquals(listOf("https://nsidc.org/data/g02135/versions/4"), opened)
    }

    @Composable
    private fun Metric(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalUnitPreferences provides METRIC, content = content)
    }

    private companion object {
        val METRIC = UnitPreferences(MeasurementSystem.METRIC)
        val OFFLINE = DataFreshness(isOffline = true)
    }
}
