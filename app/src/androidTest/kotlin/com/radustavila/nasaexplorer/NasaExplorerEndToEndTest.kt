package com.radustavila.nasaexplorer

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.radustavila.nasaexplorer.core.model.GalleryRail
import com.radustavila.nasaexplorer.core.model.MediaType
import com.radustavila.nasaexplorer.core.testing.data.TestApods
import com.radustavila.nasaexplorer.core.testing.data.TestEpicImages
import com.radustavila.nasaexplorer.core.testing.data.TestMarsPhotos
import com.radustavila.nasaexplorer.core.testing.data.TestNasaMedia
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.core.testing.fake.FakeApodRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeEpicRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeMarsPhotoRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNasaMediaRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeNetworkMonitor
import com.radustavila.nasaexplorer.core.testing.fake.FakeObserverLocationRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakePeopleInSpaceRepository
import com.radustavila.nasaexplorer.core.testing.fake.FakeSatelliteRepository
import com.radustavila.nasaexplorer.feature.details.DETAILS_CONTENT_TEST_TAG
import com.radustavila.nasaexplorer.feature.details.VIDEO_PLAYER_ERROR_TEST_TAG
import com.radustavila.nasaexplorer.feature.details.VIDEO_PLAYER_TEST_TAG
import com.radustavila.nasaexplorer.feature.earth.EARTH_PAGER_TEST_TAG
import com.radustavila.nasaexplorer.feature.earth.EARTH_PLAY_BUTTON_TEST_TAG
import com.radustavila.nasaexplorer.feature.favorites.FAVORITES_LIST_TEST_TAG
import com.radustavila.nasaexplorer.feature.feed.FEED_LIST_TEST_TAG
import com.radustavila.nasaexplorer.feature.mars.marsPhotoTestTag
import com.radustavila.nasaexplorer.feature.mars.photo.MARS_PHOTO_CONTENT_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.SKY_LIST_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.SKY_ISS_CARD_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.SKY_LOCATION_HEADER_TEST_TAG
import com.radustavila.nasaexplorer.feature.sky.component.SKY_MOON_CARD_TEST_TAG
import com.radustavila.nasaexplorer.feature.universe.details.MEDIA_DETAILS_CONTENT_TEST_TAG
import com.radustavila.nasaexplorer.feature.universe.gallery.GALLERY_RAILS_TEST_TAG
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.radustavila.nasaexplorer.core.designsystem.R as DesignSystemR
import com.radustavila.nasaexplorer.feature.earth.R as EarthR

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

    @Inject
    lateinit var nasaMediaRepository: FakeNasaMediaRepository

    @Inject
    lateinit var marsPhotoRepository: FakeMarsPhotoRepository

    @Inject
    lateinit var observerLocation: FakeObserverLocationRepository

    @Inject
    lateinit var satelliteRepository: FakeSatelliteRepository

    @Inject
    lateinit var peopleInSpace: FakePeopleInSpaceRepository

    @Inject
    lateinit var epicRepository: FakeEpicRepository

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

    @Test
    fun aLibraryItemSavedFromTheUniverseGalleryReopensFromFavorites() {
        val item = TestNasaMedia.image(nasaId = "PIA26702", title = "Webb Data Reveals Dark Matter")
        nasaMediaRepository.setRails(mapOf(GalleryRail.WEBB_GALAXIES to listOf(item)))
        launchApp()

        composeTestRule.onAllNodesWithText(string(R.string.destination_universe)).onFirst().performClick()
        composeTestRule.onNodeWithTag(GALLERY_RAILS_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(item.title).performClick()

        composeTestRule.onNodeWithTag(MEDIA_DETAILS_CONTENT_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(FAVORITE_BUTTON_TEST_TAG).performClick()
        composeTestRule.onNodeWithContentDescription(string(DesignSystemR.string.content_description_favorite_remove))
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            string(com.radustavila.nasaexplorer.feature.universe.R.string.media_details_back),
        ).performClick()

        composeTestRule.onNodeWithText(string(R.string.destination_favorites)).performClick()
        composeTestRule.onNodeWithTag(FAVORITES_LIST_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(item.title).performClick()

        composeTestRule.onNodeWithTag(MEDIA_DETAILS_CONTENT_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(item.description).assertIsDisplayed()
    }

    @Test
    fun aRoverPhotoSavedFromTheMarsRailReopensFromFavorites() {
        val photo = TestMarsPhotos.curiosity()
        nasaMediaRepository.setRails(mapOf(GalleryRail.WEBB_GALAXIES to listOf(TestNasaMedia.image())))
        marsPhotoRepository.setPhotos(listOf(photo, TestMarsPhotos.perseverance()))
        launchApp()

        composeTestRule.onAllNodesWithText(string(R.string.destination_universe)).onFirst().performClick()
        composeTestRule.onNodeWithTag(marsPhotoTestTag(photo.id)).performClick()

        composeTestRule.onNodeWithTag(MARS_PHOTO_CONTENT_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(photo.cameraName).assertIsDisplayed()
        composeTestRule.onNodeWithTag(FAVORITE_BUTTON_TEST_TAG).performClick()
        composeTestRule.onNodeWithContentDescription(string(DesignSystemR.string.content_description_favorite_remove))
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            string(com.radustavila.nasaexplorer.feature.mars.R.string.mars_photos_back),
        ).performClick()

        composeTestRule.onNodeWithText(string(R.string.destination_favorites)).performClick()
        composeTestRule.onNodeWithTag(FAVORITES_LIST_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(photo.title).performClick()

        composeTestRule.onNodeWithTag(MARS_PHOTO_CONTENT_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(photo.cameraName).assertIsDisplayed()
    }

    @Test
    fun theEarthTabOpensOnEarthTodayWithTheTabsOtherFeaturesBelow() {
        epicRepository.setImages(TestEpicImages.day(size = 3))
        launchApp()

        composeTestRule.onAllNodesWithText(string(R.string.destination_earth)).onFirst().performClick()
        // Stop the rotation first: a pager that keeps turning would never let Compose go idle.
        composeTestRule.onNodeWithTag(EARTH_PLAY_BUTTON_TEST_TAG).performClick()

        composeTestRule.onNodeWithTag(EARTH_PAGER_TEST_TAG).assertIsDisplayed()
        // Opening them is left to their own features' tests (the map would need live tiles).
        listOf(
            R.string.earth_satellite_map_title,
            R.string.earth_live_events_title,
            R.string.earth_space_weather_title,
            R.string.earth_neighbourhood_title,
        ).forEach { title -> composeTestRule.onNodeWithText(string(title)).performScrollTo().assertIsDisplayed() }
    }

    @Test
    fun anEarthPhotoSavedOnTheEarthTabReopensFromFavorites() {
        val day = TestEpicImages.day(size = 3)
        epicRepository.setImages(day)
        launchApp()

        composeTestRule.onAllNodesWithText(string(R.string.destination_earth)).onFirst().performClick()
        composeTestRule.onNodeWithTag(EARTH_PLAY_BUTTON_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(FAVORITE_BUTTON_TEST_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription(string(DesignSystemR.string.content_description_favorite_remove))
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(string(R.string.destination_favorites)).performClick()
        composeTestRule.onNodeWithTag(FAVORITES_LIST_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Earth, 16 Oct 2026", substring = true).performClick()

        // Opened for that photo: a way back, held still, and already saved.
        composeTestRule.onNodeWithContentDescription(string(EarthR.string.earth_today_back)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(EarthR.string.earth_today_play)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(DesignSystemR.string.content_description_favorite_remove))
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(EarthR.string.earth_today_back)).performClick()
        composeTestRule.onNodeWithTag(FAVORITES_LIST_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun skyTonightWorksOfflineForAPickedCity() {
        networkMonitor.setOnline(false)
        runBlocking { observerLocation.setLocation(TestSky.location()) }
        satelliteRepository.setElements(TestSky.issElements(epoch = Instant.now().minus(Duration.ofHours(6))))
        peopleInSpace.setPeople(listOf(TestSky.person()))
        // Offline, the gallery lists the tab's other features above its cached rails only.
        nasaMediaRepository.setRails(mapOf(GalleryRail.WEBB_GALAXIES to listOf(TestNasaMedia.image())))
        launchApp()

        composeTestRule.onAllNodesWithText(string(R.string.destination_universe)).onFirst().performClick()
        composeTestRule.onNodeWithText(string(R.string.universe_sky_title)).performClick()

        composeTestRule.onNodeWithTag(SKY_LOCATION_HEADER_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Bucharest").assertIsDisplayed()
        composeTestRule.waitUntil(SKY_COMPUTE_TIMEOUT_MILLIS) {
            composeTestRule.onAllNodesWithTag(SKY_MOON_CARD_TEST_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag(SKY_LIST_TEST_TAG).performScrollToNode(hasTestTag(SKY_ISS_CARD_TEST_TAG))
        composeTestRule.onNodeWithTag(SKY_ISS_CARD_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(SKY_LIST_TEST_TAG).performScrollToNode(hasText(TestSky.person().name))
        composeTestRule.onNodeWithText(TestSky.person().name).assertIsDisplayed()
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
        const val SKY_COMPUTE_TIMEOUT_MILLIS = 10_000L
    }
}
