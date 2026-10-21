package com.radustavila.nasaexplorer.feature.search

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.SpanStyle
import com.radustavila.nasaexplorer.core.domain.search.SearchResultGroup
import com.radustavila.nasaexplorer.core.domain.search.UniversalSearchResults
import com.radustavila.nasaexplorer.core.model.SearchSource
import com.radustavila.nasaexplorer.core.model.SearchTarget
import com.radustavila.nasaexplorer.core.testing.data.TestSearchHits
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h800dp")
class SearchScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val results = UniversalSearchResults(
        query = "kepler",
        groups = listOf(
            SearchResultGroup(SearchSource.EXOPLANETS, (1..5).map { TestSearchHits.exoplanet("Kepler-$it b") }),
            SearchResultGroup(SearchSource.APOD, listOf(TestSearchHits.apod("The Kepler Field"))),
        ),
        failedSources = setOf(SearchSource.LAUNCHES),
    )

    @Test
    fun `groups preview their first hits with a see all, and name failed sources`() {
        var seeAll: SearchSource? = null
        render(SearchUiState(query = "kepler", results = results), onSeeAll = { seeAll = it })

        composeTestRule.onNodeWithText("Exoplanets").assertExists()
        composeTestRule.onNodeWithText("Kepler-3 b").assertExists()
        composeTestRule.onNodeWithText("Kepler-4 b").assertDoesNotExist()
        composeTestRule.onNodeWithText("Could not search: Upcoming launches.").assertExists()

        composeTestRule.onNodeWithText("See all (5)").performClick()
        assertEquals(SearchSource.EXOPLANETS, seeAll)
    }

    @Test
    fun `an expanded group lists every hit and nothing else`() {
        render(SearchUiState(query = "kepler", results = results, expandedSource = SearchSource.EXOPLANETS))

        composeTestRule.onNodeWithText("Kepler-5 b").assertExists()
        composeTestRule.onNodeWithText("The Kepler Field").assertDoesNotExist()
    }

    @Test
    fun `a result opens its target and the online action carries the query`() {
        var opened: SearchTarget? = null
        var online: String? = null
        render(
            SearchUiState(query = " kepler ", results = results.copy(query = "kepler")),
            onResultClick = { opened = it },
            onSearchOnline = { online = it },
        )

        composeTestRule.onNodeWithText("The Kepler Field").performClick()
        composeTestRule.onNodeWithTag(SEARCH_ONLINE_BUTTON_TEST_TAG).performClick()

        assertEquals(SearchTarget.ApodEntry(TestSearchHits.apod("x").date!!), opened)
        assertEquals("kepler", online)
    }

    @Test
    fun `the online action needs a connection`() {
        render(SearchUiState(query = "kepler", results = results, isOffline = true))

        composeTestRule.onNodeWithTag(SEARCH_ONLINE_BUTTON_TEST_TAG).assertIsNotEnabled()
    }

    @Test
    fun `highlights every occurrence of every term`() {
        val text = "Kepler sees Kepler-22".highlighted(listOf("kepler", "22"), SpanStyle())

        assertEquals(listOf(0 until 6, 12 until 18, 19 until 21), text.spanStyles.map { it.start until it.end })
    }

    private fun render(
        state: SearchUiState,
        onSeeAll: (SearchSource) -> Unit = {},
        onResultClick: (SearchTarget) -> Unit = {},
        onSearchOnline: (String) -> Unit = {},
    ) {
        composeTestRule.setContent {
            SearchContent(
                uiState = state,
                onBackClick = {},
                onQueryChange = {},
                onClearQuery = {},
                onSeeAll = onSeeAll,
                onShowAllGroups = {},
                onResultClick = onResultClick,
                onSearchOnline = onSearchOnline,
            )
        }
    }
}
