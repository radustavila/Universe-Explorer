package com.radustavila.nasaexplorer.feature.sky.satellites

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.core.designsystem.notification.NotificationPermissionToggle
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.core.domain.sky.SatelliteForecast
import com.radustavila.nasaexplorer.core.domain.sky.SatelliteSetStatus
import com.radustavila.nasaexplorer.core.domain.sky.StarlinkTrain
import com.radustavila.nasaexplorer.core.domain.sky.TrackedSatellite
import com.radustavila.nasaexplorer.core.domain.sky.UpcomingPass
import com.radustavila.nasaexplorer.core.map.overlay.MapOverlay
import com.radustavila.nasaexplorer.core.map.overlay.MapPolyline
import com.radustavila.nasaexplorer.core.model.SatelliteSet
import com.radustavila.nasaexplorer.core.testing.data.TestSatellites
import com.radustavila.nasaexplorer.core.testing.data.TestSky
import com.radustavila.nasaexplorer.feature.sky.R
import com.radustavila.nasaexplorer.feature.sky.component.ISS_TRACK_OVERLAY_ID
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2000dp")
class MoreSatellitesCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val start = Instant.parse("2026-10-21T17:00:00Z")
    private val hubblePass = UpcomingPass(
        TrackedSatellite(SatelliteSet.HUBBLE, TestSatellites.hubbleElements()),
        TestSky.pass(start = start),
    )
    private val trainPass = UpcomingPass(
        TrackedSatellite(
            SatelliteSet.STARLINK_TRAINS,
            TestSatellites.starlinkElements(noradId = 101_001),
            StarlinkTrain(launch = "2026-240", launchedAt = Instant.parse("2026-10-18T12:00:00Z"), satelliteCount = 24),
        ),
        TestSky.pass(start = start.plus(Duration.ofHours(1))),
    )
    private val brightPass = UpcomingPass(
        TrackedSatellite(
            SatelliteSet.BRIGHT_SATELLITES,
            TestSatellites.hubbleElements().copy(noradId = 694, name = "ATLAS CENTAUR 2"),
        ),
        TestSky.pass(start = start.plus(Duration.ofHours(2))),
    )
    private var shownOverlays: List<MapOverlay> = emptyList()

    @Test
    fun `with nothing picked it explains the picker, and a chip turns a set on`() {
        val toggled = mutableListOf<Pair<SatelliteSet, Boolean>>()
        setContent(MoreSatellitesUiState(), onSetToggle = { set, on -> toggled += set to on })

        composeTestRule.onNodeWithText(string(R.string.sky_satellites_pick)).assertExists()
        composeTestRule.onNodeWithTag(skySatelliteChipTestTag(SatelliteSet.HUBBLE)).performClick()

        assertEquals(listOf(SatelliteSet.HUBBLE to true), toggled)
    }

    @Test
    fun `passes of the picked sets are listed with what and when, magnitude for named ones`() {
        setContent(forecastState())

        composeTestRule.onNodeWithTag(skySatelliteChipTestTag(SatelliteSet.HUBBLE)).assertIsSelected()
        composeTestRule.onAllNodesWithTag(SKY_SATELLITES_PASS_TEST_TAG).assertCountEquals(3)
        composeTestRule.onAllNodesWithTag(SKY_SATELLITES_PASS_TEST_TAG)[0].assert(hasText("Hubble"))
        composeTestRule.onNodeWithText("typically magnitude 1.5", substring = true).assertExists()
        composeTestRule.onNodeWithText("Starlink train · 24 satellites, launched", substring = true).assertExists()
        composeTestRule.onNodeWithText("ATLAS CENTAUR 2").assertExists()
    }

    @Test
    fun `a set without passes says why`() {
        setContent(
            forecastState(
                passes = emptyList(),
                status = mapOf(
                    SatelliteSet.HUBBLE to SatelliteSetStatus.READY,
                    SatelliteSet.STARLINK_TRAINS to SatelliteSetStatus.NO_RECENT_LAUNCH,
                    SatelliteSet.BRIGHT_SATELLITES to SatelliteSetStatus.NO_DATA,
                ),
            ),
        )

        val noPasses = context.getString(R.string.sky_satellites_status_no_passes, "Hubble")
        composeTestRule.onNodeWithText(noPasses).assertExists()
        composeTestRule.onNodeWithText(string(R.string.sky_satellites_status_no_launch)).assertExists()
        val noData = context.getString(R.string.sky_satellites_status_no_data, "Bright satellites")
        composeTestRule.onNodeWithText(noData).assertExists()
    }

    @Test
    fun `tapping a pass asks for its track, an open pass shows it on the map`() {
        val clicked = mutableListOf<UpcomingPass>()
        setContent(forecastState(), onPassClick = { clicked += it })
        composeTestRule.onAllNodesWithTag(SKY_SATELLITES_PASS_TEST_TAG)[1].performClick()
        assertEquals(listOf(trainPass), clicked)

        setContent(
            forecastState().copy(
                selected = SelectedPass(
                    trainPass,
                    track = listOf(TestSky.position(longitude = 20.0), TestSky.position(longitude = 30.0)),
                ),
            ),
        )

        composeTestRule.onNodeWithTag(SKY_SATELLITES_TRACK_TEST_TAG).assertExists()
        val track = shownOverlays.single { it.id == ISS_TRACK_OVERLAY_ID }.shapes.single() as MapPolyline
        assertEquals(2, track.points.size)
    }

    @Test
    fun `each picked set with reminders has its own switch`() {
        var clicks = 0
        setContent(
            forecastState(),
            reminders = mapOf(SatelliteSet.HUBBLE to NotificationPermissionToggle(isOn = true, onClick = { clicks++ })),
        )

        composeTestRule.onNodeWithTag(skySatelliteReminderTestTag(SatelliteSet.HUBBLE)).assertIsOn().performClick()
        composeTestRule.onNodeWithTag(skySatelliteReminderTestTag(SatelliteSet.BRIGHT_SATELLITES)).assertDoesNotExist()
        assertEquals(1, clicks)
    }

    private fun forecastState(
        passes: List<UpcomingPass> = listOf(hubblePass, trainPass, brightPass),
        status: Map<SatelliteSet, SatelliteSetStatus> = mapOf(
            SatelliteSet.HUBBLE to SatelliteSetStatus.READY,
            SatelliteSet.STARLINK_TRAINS to SatelliteSetStatus.READY,
            SatelliteSet.BRIGHT_SATELLITES to SatelliteSetStatus.READY,
        ),
    ) = MoreSatellitesUiState(
        enabledSets = status.keys,
        forecast = SatelliteForecast(
            enabledSets = status.keys,
            passes = passes,
            setStatus = status,
            computedAt = start,
        ),
        observer = TestSky.BUCHAREST,
    )

    private var content: androidx.compose.runtime.MutableState<MoreSatellitesUiState>? = null

    private fun setContent(
        state: MoreSatellitesUiState,
        reminders: Map<SatelliteSet, NotificationPermissionToggle> = emptyMap(),
        onSetToggle: (SatelliteSet, Boolean) -> Unit = { _, _ -> },
        onPassClick: (UpcomingPass) -> Unit = {},
    ) {
        content?.let {
            it.value = state
            composeTestRule.waitForIdle()
            return
        }
        val holder = androidx.compose.runtime.mutableStateOf(state)
        content = holder
        composeTestRule.setContent {
            NasaTheme {
                MoreSatellitesCard(
                    uiState = holder.value,
                    reminders = reminders,
                    map = { overlays, _, modifier ->
                        shownOverlays = overlays
                        Box(modifier.testTag("fake-map"))
                    },
                    onSetToggle = onSetToggle,
                    onPassClick = onPassClick,
                )
            }
        }
    }

    private fun string(id: Int): String = context.getString(id)
}
