package com.radustavila.nasaexplorer.data.spaceweather.sun

import com.radustavila.nasaexplorer.core.model.SunChannel
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val SMALL_PIXELS = 512
private const val MEDIUM_PIXELS = 1024
private const val LARGE_PIXELS = 2048

/** The square sizes SDO publishes its latest pictures and videos in. */
enum class SdoImageSize(val pixels: Int) {
    /** About 50 KB a picture; the size of the 48-hour videos the app plays (3–10 MB). */
    SMALL(SMALL_PIXELS),

    /** About 200–400 KB: a phone-wide card. */
    MEDIUM(MEDIUM_PIXELS),

    /** About 0.6–0.9 MB: full screen with zoom. */
    LARGE(LARGE_PIXELS),
}

/** The file name part SDO gives [this] channel in `latest_{size}_{channel}.jpg`. */
val SunChannel.sdoCode: String
    get() = when (this) {
        SunChannel.VISIBLE_LIGHT -> "HMIIC"
        SunChannel.MAGNETIC_FIELD -> "HMIB"
        SunChannel.CHROMOSPHERE -> "0304"
        SunChannel.CORONA -> "0171"
        SunChannel.HOT_CORONA -> "0193"
        SunChannel.FLARES -> "0131"
    }

/**
 * Builds the URLs of SDO's latest pictures and videos and of Helioviewer's pictures of a past moment.
 *
 * SDO (https://sdo.gsfc.nasa.gov/assets/img/latest/) replaces each file in place about every 15
 * minutes, under the same name. Helioviewer renders a picture of any moment since 2010 from the
 * frame nearest it; its `takeScreenshot` call answers with the PNG itself when asked to `display` it.
 */
class SdoImagery(
    private val sdoBaseUrl: String,
    private val helioviewerBaseUrl: String,
) {
    fun stillFileName(channel: SunChannel, size: SdoImageSize): String = "latest_${size.pixels}_${channel.sdoCode}.jpg"

    fun stillUrl(channel: SunChannel, size: SdoImageSize): String =
        "$sdoBaseUrl$LATEST_PATH${stillFileName(channel, size)}"

    fun videoUrl(channel: SunChannel, size: SdoImageSize): String =
        "$sdoBaseUrl${LATEST_PATH}mpeg/latest_${size.pixels}_${channel.sdoCode}.mp4"

    /**
     * The whole disc at [time] in AIA 131 Å, the channel that shows flares best, as a [pixels]-wide
     * PNG. The scale fits the disc (about 1,920 arcseconds across) with a little corona around it.
     */
    fun helioviewerImageUrl(time: Instant, pixels: Int = HELIOVIEWER_PIXELS): String {
        val arcsecondsPerPixel = FIELD_OF_VIEW_ARCSECONDS / pixels
        return helioviewerBaseUrl + "v2/takeScreenshot/" +
            "?date=${time.truncatedTo(ChronoUnit.SECONDS)}" +
            "&imageScale=$arcsecondsPerPixel" +
            // [sourceId,visible,opacity]; source 9 is SDO AIA 131.
            "&layers=%5B$HELIOVIEWER_AIA_131,1,100%5D" +
            "&x0=0&y0=0&width=$pixels&height=$pixels" +
            "&display=true&watermark=false"
    }

    companion object {
        /** Where SDO keeps its latest files, relative to its base URL. */
        const val LATEST_PATH = "assets/img/latest/"

        private const val HELIOVIEWER_AIA_131 = 9
        private const val HELIOVIEWER_PIXELS = 1024
        private const val FIELD_OF_VIEW_ARCSECONDS = 2457.6
    }
}
