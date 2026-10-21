package com.radustavila.nasaexplorer.feature.settings.about

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AboutScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val opened = mutableListOf<String>()
    private var licencesOpened = 0

    private fun setScreen() {
        composeTestRule.setContent {
            AboutContent(
                version = AppVersion(label = "NASA Explorer", name = "1.2.0", code = 12),
                onBackClick = {},
                onLicencesClick = { licencesOpened++ },
                onOpenUrl = { opened += it },
            )
        }
    }

    @Test
    fun `shows the version and opens the licences`() {
        setScreen()

        composeTestRule.onNodeWithText("Version 1.2.0 (12)").assertExists()
        composeTestRule.onNodeWithTag(ABOUT_LICENCES_TEST_TAG).performClick()

        assertEquals(1, licencesOpened)
    }

    @Test
    fun `every data source is credited with a link`() {
        setScreen()

        DATA_CREDITS.forEach { credit ->
            composeTestRule.onNodeWithTag(ABOUT_LIST_TEST_TAG).performScrollToNode(hasTestTag(aboutCreditTestTag(credit.url)))
            composeTestRule.onNodeWithTag(aboutCreditTestTag(credit.url)).performClick()
        }

        assertEquals(DATA_CREDITS.map { it.url }, opened)
        assertTrue(opened.all { it.startsWith("https://") })
    }

    @Test
    fun `the credits cover every source the app reads`() {
        val urls = DATA_CREDITS.map { it.url }
        listOf(
            "apod.nasa.gov", "epic.gsfc.nasa.gov", "gibs", "eonet", "images.nasa.gov", "exoplanetarchive",
            "swpc.noaa.gov", "DONKI", "jpl.nasa.gov", "mars.nasa.gov", "celestrak", "wheretheiss.at",
            "thespacedevs", "cosinekitty", "maplibre",
        ).forEach { source -> assertTrue(source, urls.any { it.contains(source) }) }
    }
}
