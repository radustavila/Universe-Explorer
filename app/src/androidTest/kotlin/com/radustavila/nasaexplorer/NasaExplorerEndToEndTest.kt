package com.radustavila.nasaexplorer

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import com.radustavila.nasaexplorer.core.designsystem.component.FAVORITE_BUTTON_TEST_TAG
import com.radustavila.nasaexplorer.core.designsystem.component.OFFLINE_BANNER_TEST_TAG
import com.radustavila.nasaexplorer.core.model.Apod
import com.radustavila.nasaexplorer.core.model.MediaType
import com.radustavila.nasaexplorer.core.testing.data.TestApods
import com.radustavila.nasaexplorer.core.testing.fake.FakeApodRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.feature.details.DETAILS_CONTENT_TEST_TAG
import com.radustavila.nasaexplorer.feature.details.VIDEO_PLAYER_ERROR_TEST_TAG
import com.radustavila.nasaexplorer.feature.details.VIDEO_PLAYER_TEST_TAG
import com.radustavila.nasaexplorer.feature.favorites.FAVORITES_LIST_TEST_TAG
import com.radustavila.nasaexplorer.feature.feed.FEED_LIST_TEST_TAG
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalDate
import javax.inject.Inject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.radustavila.nasaexplorer.core.designsystem.R as DesignSystemR

/**
 * End-to-end coverage on a real device/emulator: the real Activity, the real navigation graph, the
 * real ViewModels and Compose UI, with only the repository and the connectivity monitor faked.
 *
 * These complement - they do not replace - the Robolectric screen tests, which exercise individual
 * screens against a hand-built state. What only a device can show is the wiring: cold start,
 * navigation between features, and state surviving a hop across screens.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NasaExplorerEndToEndTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    /**
     * The Activity is launched by each test *after* the cache has been seeded, so "cold start with
     * a populated cache" is actually what is being measured; an Activity-launching rule would start
     * it too early.
     */
    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var apodRepository: FakeApodRepository

    @Inject
    lateinit var networkMonitor: FakeNetworkMonitor

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        // Images must never touch the network here: a pending request would leave an indeterminate
        // spinner on screen, which Compose's idling never settles on.
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(ApplicationProvider.getApplicationContext<Context>())
                .networkCachePolicy(CachePolicy.DISABLED)
                .build(),
        )
    }

    @After
    fun tearDown() {
        scenario?.close()
        SingletonImageLoader.reset()
    }

    @Test
    fun coldStartRendersTheCachedFeed() {
        apodRepository.setEntries(TestApods.feed(size = 3))

        launchApp()

        composeTestRule.onNodeWithTag(FEED_LIST_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Apod #0").assertIsDisplayed()

        // The oldest entry starts below the fold, so reaching it also proves the list scrolls.
        composeTestRule.onNodeWithTag(FEED_LIST_TEST_TAG)
            .performScrollToNode(hasText("Apod #2"))
        composeTestRule.onNodeWithText("Apod #2").assertIsDisplayed()
    }

    @Test
    fun tappingAnEntryOpensItsDetails() {
        val apod = TestApods.image()
        apodRepository.setEntries(listOf(apod))

        launchApp()
        composeTestRule.onNodeWithText(apod.title).performClick()

        composeTestRule.onNodeWithTag(DETAILS_CONTENT_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(apod.explanation).assertIsDisplayed()
    }

    @Test
    fun favoritingFromTheFeedShowsTheEntryOnTheFavoritesTab() {
        val apod = TestApods.image(isFavorite = false)
        apodRepository.setEntries(listOf(apod))

        launchApp()
        composeTestRule.onAllNodesWithTag(FAVORITE_BUTTON_TEST_TAG).onFirst().performClick()

        composeTestRule
            .onNodeWithContentDescription(
                string(DesignSystemR.string.content_description_favorite_remove),
            )
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(string(R.string.destination_favorites)).performClick()

        composeTestRule.onNodeWithTag(FAVORITES_LIST_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(apod.title).assertIsDisplayed()
    }

    @Test
    fun offlineStartStillRendersTheCachedFeedBehindTheStaleBanner() {
        networkMonitor.setOnline(false)
        apodRepository.setEntries(TestApods.feed(size = 2))

        launchApp()

        composeTestRule.onNodeWithTag(OFFLINE_BANNER_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(FEED_LIST_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Apod #0").assertIsDisplayed()
    }

    @Test
    fun anUnreachableVideoStreamFailsInsideThePlayerInsteadOfLeavingTheApp() {
        val video = unreachableVideo()
        apodRepository.setEntries(listOf(video))

        launchApp()
        composeTestRule.onNodeWithText(video.title).performClick()

        composeTestRule.onNodeWithTag(VIDEO_PLAYER_TEST_TAG).assertIsDisplayed()
        composeTestRule.waitUntil(PLAYBACK_TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodesWithTag(VIDEO_PLAYER_ERROR_TEST_TAG)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun launchApp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun string(resourceId: Int): String =
        ApplicationProvider.getApplicationContext<Context>().getString(resourceId)

    /**
     * A stream on a closed local port: ExoPlayer fails immediately and deterministically, with no
     * DNS lookup and no dependency on the emulator having a network.
     */
    private fun unreachableVideo(): Apod = Apod(
        date = LocalDate.of(2026, 9, 8),
        title = "Perseverance Lands on Mars",
        explanation = "Descent footage from the rover.",
        mediaType = MediaType.VIDEO,
        url = "http://127.0.0.1:1/perseverance.mp4",
    )

    private companion object {
        const val PLAYBACK_TIMEOUT_MILLIS = 10_000L
    }
}
