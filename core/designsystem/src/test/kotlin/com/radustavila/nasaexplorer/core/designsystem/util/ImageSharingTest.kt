package com.radustavila.nasaexplorer.core.designsystem.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImageSharingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val content = ShareContent(
        title = "Spiral Galaxy NGC 1300",
        details = listOf("10 Sept 2026", "© Hubble Heritage Team,\n   ESA,  NASA"),
        link = "https://science.nasa.gov/image-article/apod-2026-september-10/",
        imageUrl = "https://apod.nasa.gov/apod/image/ngc1300.jpg",
        cardSubtitle = "10 Sept 2026 · Astronomy Picture of the Day",
    )

    @Test
    fun `the caption is title, details and link, one line each`() {
        assertEquals(
            "Spiral Galaxy NGC 1300\n" +
                "10 Sept 2026\n" +
                "© Hubble Heritage Team, ESA, NASA\n" +
                "https://science.nasa.gov/image-article/apod-2026-september-10/",
            content.caption,
        )
        assertEquals("Mars", ShareContent(title = "Mars", details = listOf(" ", "")).caption)
    }

    @Test
    fun `with a card the intent sends the image, readable by the receiver, with the caption as text`() {
        val image = Uri.parse("content://com.radustavila.nasaexplorer.share/share/card.jpg")

        val intent = buildShareIntent(content, image)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/jpeg", intent.type)
        assertEquals(image, IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals(content.caption, intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(content.title, intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        // The share sheet previews, and grants access to, what the clip data names.
        assertEquals(image, intent.clipData?.getItemAt(0)?.uri)
    }

    @Test
    fun `without a card the intent falls back to the caption alone`() {
        val intent = buildShareIntent(content, image = null)

        assertEquals("text/plain", intent.type)
        assertEquals(content.caption, intent.getStringExtra(Intent.EXTRA_TEXT))
        assertNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        assertNull(intent.clipData)
        assertFalse(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun `the card keeps the picture's proportions and adds a strip below it`() {
        val picture = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)

        val card = renderShareCard(picture, content.title, content.cardSubtitle)

        assertEquals(1600, card.width)
        assertTrue(card.height > 1200)
    }

    @Test
    fun `a small picture is scaled up so the text on its card stays legible`() {
        val card = renderShareCard(Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888), "Mars", subtitle = null)

        assertEquals(1080, card.width)
        assertTrue(card.height > 810)
    }

    @Test
    fun `cards are served from the share directory through the app's file provider`() {
        val file = context.shareDirectory().resolve("card.jpg").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1))
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)

        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.share", uri.authority)
        assertEquals("/share/card.jpg", uri.path)
    }

    @Test
    fun `file names are readable slugs of the title`() {
        assertEquals("spiral-galaxy-ngc-1300", slugOf("Spiral Galaxy NGC 1300"))
        assertEquals("earth-16-oct-2026-12-36-utc", slugOf("Earth, 16 Oct 2026, 12:36 UTC"))
        assertEquals("nasa-explorer", slugOf("???"))
        assertTrue(slugOf("a".repeat(200)).length <= 48)
    }
}
