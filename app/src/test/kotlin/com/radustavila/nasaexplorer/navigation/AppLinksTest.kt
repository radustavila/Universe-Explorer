package com.radustavila.nasaexplorer.navigation

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.XmlResourceParser
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.createGraph
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.MainActivity
import com.radustavila.nasaexplorer.R
import com.radustavila.nasaexplorer.feature.details.DetailsRoute
import com.radustavila.nasaexplorer.feature.details.detailsScreen
import com.radustavila.nasaexplorer.feature.earthmap.earthMapScreen
import com.radustavila.nasaexplorer.feature.feed.FeedRoute
import com.radustavila.nasaexplorer.feature.feed.feedScreen
import com.radustavila.nasaexplorer.feature.search.SearchRoute
import com.radustavila.nasaexplorer.feature.search.searchScreen
import com.radustavila.nasaexplorer.feature.sky.skyTonightScreen
import com.radustavila.nasaexplorer.feature.spaceweather.spaceWeatherScreen
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * The ways into the app from outside it: `apod.nasa.gov` links (the manifest's intent filter and
 * [ApodWebLinks]) and the launcher shortcuts (`res/xml/shortcuts.xml`), down to the screen each one
 * reaches through the features' own deep links.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AppLinksTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val today = LocalDate.of(2026, 10, 21)

    @Test
    fun `apod site pages resolve to the main activity`() {
        listOf(
            "https://apod.nasa.gov/apod/ap240101.html",
            "http://apod.nasa.gov/apod/ap991231.html",
            "https://apod.nasa.gov/apod/astropix.html",
        ).forEach { url ->
            val activities = context.packageManager.queryIntentActivities(webIntent(url), 0)
            assertEquals(url, listOf(MainActivity::class.java.name), activities.map { it.activityInfo.name })
        }
    }

    @Test
    fun `the rest of the web is not claimed`() {
        listOf(
            "https://apod.nasa.gov/apod/archivepix.html",
            "https://apod.nasa.gov/",
            "https://science.nasa.gov/apod/",
            "https://example.com/apod/ap240101.html",
        ).forEach { url ->
            assertTrue(url, context.packageManager.queryIntentActivities(webIntent(url), 0).isEmpty())
        }
    }

    @Test
    fun `an apod site page becomes the details deep link of its day`() {
        assertEquals(
            "nasaexplorer://apod/2024-01-01".toUri(),
            ApodWebLinks.inAppUriFor(webIntent("https://apod.nasa.gov/apod/ap240101.html"), today),
        )
        assertEquals(
            "nasaexplorer://apod/2026-10-21".toUri(),
            ApodWebLinks.inAppUriFor(webIntent("https://apod.nasa.gov/apod/astropix.html"), today),
        )
        // Claimed by the filter's wildcard but not a day: left for the browser.
        assertNull(ApodWebLinks.inAppUriFor(webIntent("https://apod.nasa.gov/apod/apod_history.html"), today))
        assertFalse(ApodWebLinks.isWebLink(Intent(Intent.ACTION_VIEW, "nasaexplorer://search".toUri())))
        assertFalse(ApodWebLinks.isWebLink(Intent(Intent.ACTION_MAIN)))
    }

    @Test
    fun `a rewritten web link opens the details of that day`() {
        val navController = navController()
        val uri = requireNotNull(ApodWebLinks.inAppUriFor(webIntent("https://apod.nasa.gov/apod/ap240101.html"), today))

        assertTrue(navController.handleDeepLink(Intent(Intent.ACTION_VIEW, uri)))

        val entry = requireNotNull(navController.currentBackStackEntry)
        assertEquals("2024-01-01", entry.toRoute<DetailsRoute>().date)
    }

    @Test
    fun `every launcher shortcut opens a screen of the app`() {
        val shortcuts = shortcutUris()
        assertEquals(
            listOf(
                "nasaexplorer://search",
                "nasaexplorer://today",
                "nasaexplorer://sky",
                "nasaexplorer://spaceweather",
                "nasaexplorer://earthmap",
            ),
            shortcuts,
        )

        val graph = navController().graph
        shortcuts.forEach { uri -> assertTrue(uri, graph.hasDeepLink(uri.toUri())) }
    }

    @Test
    fun `the search shortcut can carry a query`() {
        val navController = navController()

        assertTrue(navController.handleDeepLink(Intent(Intent.ACTION_VIEW, "nasaexplorer://search?query=crab".toUri())))

        assertEquals("crab", requireNotNull(navController.currentBackStackEntry).toRoute<SearchRoute>().query)
    }

    private fun webIntent(url: String) = Intent(Intent.ACTION_VIEW, url.toUri())
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .setPackage(context.packageName)

    /** The destinations the shortcuts and web links lead to, registered by their features' extensions. */
    private fun navController() = NavHostController(context).apply {
        navigatorProvider.addNavigator(ComposeNavigator())
        setViewModelStore(ViewModelStore())
        graph = createGraph(startDestination = FeedRoute) {
            feedScreen(onApodClick = {})
            detailsScreen(onBackClick = {}, onApodClick = {})
            searchScreen(onBackClick = {}, onResultClick = {}, onSearchOnline = {})
            skyTonightScreen(onBackClick = {})
            spaceWeatherScreen(onBackClick = {})
            earthMapScreen(onBackClick = {})
        }
    }

    private fun shortcutUris(): List<String> {
        val parser: XmlResourceParser = context.resources.getXml(R.xml.shortcuts)
        val uris = mutableListOf<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "intent") {
                uris += requireNotNull(parser.getAttributeValue(ANDROID_NAMESPACE, "data"))
            }
        }
        return uris
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
