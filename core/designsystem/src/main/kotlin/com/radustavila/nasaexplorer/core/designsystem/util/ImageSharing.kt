package com.radustavila.nasaexplorer.core.designsystem.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * What a share sheet sends for one item: a caption, and the picture to attach when there is one.
 *
 * The caption is [title], then each of [details] (date, credit, …) on its own line, then [link].
 * [cardSubtitle] is the small print under the title on the image card.
 */
data class ShareContent(
    val title: String,
    val details: List<String> = emptyList(),
    val link: String? = null,
    val imageUrl: String? = null,
    val cardSubtitle: String? = null,
) {
    val caption: String
        get() = (listOf(title) + details + listOfNotNull(link))
            // NASA credits arrive across several lines; one line each reads better in a message.
            .map { line -> line.replace(WHITESPACE, " ").trim() }
            .filter(String::isNotEmpty)
            .joinToString(separator = "\n")
}

/**
 * Opens the share sheet for [content]: the picture as a branded image card with the caption as its
 * text, or the caption alone when there is no picture or it cannot be loaded (offline and not
 * cached, say).
 *
 * The picture comes through the application's image loader - so a favourite's offline copy, the
 * disk cache and the video frame decoder all apply - and the card is drawn and written on the
 * loader's decoder dispatcher, off the main thread.
 */
suspend fun Context.shareImageCard(content: ShareContent, chooserTitle: String) {
    val card = content.imageUrl?.let { url ->
        try {
            writeShareCard(url, content)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            // No provider path for the file: a packaging error, but sharing the text still works.
            null
        }
    }
    try {
        startActivity(
            Intent.createChooser(buildShareIntent(content, card), chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
        // No share targets installed.
    }
}

/**
 * The `ACTION_SEND` intent for [content]: a JPEG at [image] (readable by whichever app is picked)
 * with the caption as its text, or plain text without an image.
 */
fun buildShareIntent(content: ShareContent, image: Uri?): Intent {
    val send = Intent(Intent.ACTION_SEND)
        .putExtra(Intent.EXTRA_SUBJECT, content.title)
        .putExtra(Intent.EXTRA_TEXT, content.caption)
    if (image == null) return send.setType(TEXT_MIME_TYPE)

    return send.setType(SHARE_CARD_MIME_TYPE)
        .putExtra(Intent.EXTRA_STREAM, image)
        // The share sheet's preview title.
        .putExtra(Intent.EXTRA_TITLE, content.title)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .apply { clipData = ClipData.newRawUri(content.title, image) }
}

/** Loads the picture, draws its card and returns a shareable URI for it, or `null` without one. */
private suspend fun Context.writeShareCard(url: String, content: ShareContent): Uri? {
    val request = ImageRequest.Builder(this)
        .data(url)
        .size(CARD_PICTURE_MAX_PX)
        .allowHardware(false)
        .build()
    val result = SingletonImageLoader.get(this).execute(request) as? SuccessResult ?: return null

    return withContext(result.request.decoderCoroutineContext) {
        val picture = result.image.toBitmap()
        val card = renderShareCard(picture, content.title, content.cardSubtitle)
        try {
            val file = writeShareFile(card, content.title)
            FileProvider.getUriForFile(this@writeShareCard, packageName + SHARE_AUTHORITY_SUFFIX, file)
        } finally {
            card.recycle()
        }
    }
}

/**
 * The picture with a strip underneath: an accent rule, the title (up to two lines) and [subtitle].
 * Text is sized relative to the card, so it reads the same at any resolution; a small picture is
 * scaled up to [CARD_MIN_WIDTH_PX] so its text stays legible.
 */
internal fun renderShareCard(picture: Bitmap, title: String, subtitle: String?): Bitmap {
    val width = maxOf(picture.width, CARD_MIN_WIDTH_PX)
    val pictureHeight = (picture.height * width.toFloat() / picture.width).roundToInt()
    val padding = width * PADDING_RATIO

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TITLE_COLOR
        textSize = width * TITLE_SIZE_RATIO
        typeface = Typeface.DEFAULT_BOLD
    }
    val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = SUBTITLE_COLOR
        textSize = width * SUBTITLE_SIZE_RATIO
    }
    val textWidth = (width - 2 * padding).roundToInt()
    val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, textWidth)
        .setMaxLines(TITLE_MAX_LINES)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()
    val subtitleText = subtitle?.let { text ->
        TextUtils.ellipsize(text, subtitlePaint, textWidth.toFloat(), TextUtils.TruncateAt.END)
    }
    val gap = padding * GAP_RATIO
    val subtitleHeight = if (subtitleText == null) 0f else gap + subtitlePaint.fontSpacing
    val stripHeight = (2 * padding + titleLayout.height + subtitleHeight).roundToInt()

    val card = createBitmap(width, pictureHeight + stripHeight)
    Canvas(card).apply {
        drawBitmap(picture, null, Rect(0, 0, width, pictureHeight), Paint(Paint.FILTER_BITMAP_FLAG))
        val stripTop = pictureHeight.toFloat()
        drawRect(0f, stripTop, width.toFloat(), card.height.toFloat(), Paint().apply { color = STRIP_COLOR })
        drawRect(0f, stripTop, width.toFloat(), stripTop + width * ACCENT_RATIO, Paint().apply { color = ACCENT_COLOR })

        withTranslation(padding, stripTop + padding) { titleLayout.draw(this) }

        if (subtitleText != null) {
            val baseline = stripTop + padding + titleLayout.height + gap - subtitlePaint.ascent()
            drawText(subtitleText, 0, subtitleText.length, padding, baseline, subtitlePaint)
        }
    }
    return card
}

/**
 * Writes [card] to the share directory in the cache, which the file provider exposes. Cards older
 * than [STALE_CARD_MILLIS] go first: by then the app that received one has long read it, and the
 * system may clear the cache before that anyway.
 */
private fun Context.writeShareFile(card: Bitmap, title: String): File {
    val directory = shareDirectory().apply { mkdirs() }
    val now = System.currentTimeMillis()
    directory.listFiles()
        ?.filter { now - it.lastModified() > STALE_CARD_MILLIS }
        ?.forEach(File::delete)

    val file = directory.resolve("${slugOf(title)}-$now.jpg")
    file.outputStream().use { output ->
        if (!card.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
            throw IOException("Could not encode the share card")
        }
    }
    return file
}

/** Where share cards are written; must match `res/xml/share_paths.xml`. */
internal fun Context.shareDirectory(): File = cacheDir.resolve(SHARE_DIRECTORY)

/** A readable file name for the receiving app: `spiral-galaxy-ngc-1300`. */
internal fun slugOf(title: String): String = title.lowercase()
    .replace(NON_SLUG, "-")
    .trim('-')
    .take(SLUG_MAX_LENGTH)
    .trimEnd('-')
    .ifEmpty { "nasa-explorer" }

/**
 * Serves share cards to other apps, read-only and only for the duration of a share. Its own class so
 * it never merges with another library's `FileProvider` declaration.
 */
class ShareFileProvider : FileProvider()

private const val SHARE_AUTHORITY_SUFFIX = ".share"
private const val SHARE_DIRECTORY = "share"
private const val SHARE_CARD_MIME_TYPE = "image/jpeg"
private const val TEXT_MIME_TYPE = "text/plain"
private const val STALE_CARD_MILLIS = 6L * 60 * 60 * 1000
private const val JPEG_QUALITY = 90
private const val SLUG_MAX_LENGTH = 48

private const val CARD_PICTURE_MAX_PX = 1600
private const val CARD_MIN_WIDTH_PX = 1080
private const val PADDING_RATIO = 0.05f
private const val GAP_RATIO = 0.35f
private const val ACCENT_RATIO = 0.006f
private const val TITLE_SIZE_RATIO = 0.05f
private const val SUBTITLE_SIZE_RATIO = 0.032f
private const val TITLE_MAX_LINES = 2

// The app's dark palette (see theme/Color.kt): deep space ground, moon grey text, lavender accent.
private const val STRIP_COLOR = 0xFF07070D.toInt()
private const val TITLE_COLOR = 0xFFECECF3.toInt()
private const val SUBTITLE_COLOR = 0xFF9E9EB0.toInt()
private const val ACCENT_COLOR = 0xFFCFC2FF.toInt()

private val WHITESPACE = Regex("\\s+")
private val NON_SLUG = Regex("[^a-z0-9]+")
