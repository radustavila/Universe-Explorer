package com.radustavila.nasaexplorer.feature.widget.earth

import android.graphics.Bitmap
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import com.radustavila.nasaexplorer.core.model.EpicImage
import com.radustavila.nasaexplorer.core.testing.data.TestEpicImages
import com.radustavila.nasaexplorer.feature.widget.WidgetSizes
import com.radustavila.nasaexplorer.feature.widget.testContext
import com.radustavila.nasaexplorer.feature.widget.testResources
import com.radustavila.nasaexplorer.feature.widget.testWidgetFormat
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EarthTodayWidgetTest {

    private val now = Instant.parse("2026-10-17T09:00:00Z")
    private val day = TestEpicImages.day(size = 3)

    @Test
    fun `the newest photo of the day is shown, with when it was taken`() {
        val photo = checkNotNull(day.shuffled().newestPhoto())

        val state = earthTodayWidgetState(photo, now, testWidgetFormat(), testResources)

        assertEquals(day.last(), photo)
        assertEquals("Earth · Fri 16 Oct 03:58", state.caption)
        assertEquals("${photo.caption} Taken Fri 16 Oct 03:58.", state.pictureDescription)
        assertNull(emptyList<EpicImage>().newestPhoto())
    }

    @Test
    fun `the picture carries the description`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(WidgetSizes.Small)
        setContext(testContext)
        val state = EarthTodayWidgetState(caption = "Earth · 03:58", pictureDescription = "Earth from DSCOVR")
        val picture = Bitmap.createBitmap(8, 8, Bitmap.Config.RGB_565)
        provideComposable { EarthTodayWidgetContent(state, picture, openIntent = null) }

        onNode(hasContentDescription("Earth from DSCOVR")).assertExists()
        onNode(hasText("Earth · 03:58")).assertExists()
    }

    @Test
    fun `offline without the picture the description is spelled out`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(WidgetSizes.Small)
        setContext(testContext)
        val state = EarthTodayWidgetState(caption = "Earth · 03:58", pictureDescription = "Earth from DSCOVR")
        provideComposable { EarthTodayWidgetContent(state, picture = null, openIntent = null) }

        onNode(hasText("Earth from DSCOVR")).assertExists()
    }
}
