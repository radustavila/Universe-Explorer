package com.radustavila.nasaexplorer.data.spaceweather.sun

import com.radustavila.nasaexplorer.core.model.SunChannel
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SdoImageryTest {

    private val imagery = SdoImagery(
        sdoBaseUrl = "https://sdo.gsfc.nasa.gov/",
        helioviewerBaseUrl = "https://api.helioviewer.org/",
    )

    /** SDO's own names, checked against the server on 2026-10-22. */
    private val codes = mapOf(
        SunChannel.VISIBLE_LIGHT to "HMIIC",
        SunChannel.MAGNETIC_FIELD to "HMIB",
        SunChannel.CHROMOSPHERE to "0304",
        SunChannel.CORONA to "0171",
        SunChannel.HOT_CORONA to "0193",
        SunChannel.FLARES to "0131",
    )

    @Test
    fun `every channel has its SDO code`() {
        assertEquals(codes, SunChannel.entries.associateWith { it.sdoCode })
    }

    @Test
    fun `stills are latest_size_channel jpg for every size and channel`() {
        for (channel in SunChannel.entries) {
            for ((size, pixels) in SdoImageSize.entries.map { it to it.pixels }) {
                assertEquals(
                    "https://sdo.gsfc.nasa.gov/assets/img/latest/latest_${pixels}_${codes.getValue(channel)}.jpg",
                    imagery.stillUrl(channel, size),
                )
            }
        }
        assertEquals(listOf(512, 1024, 2048), SdoImageSize.entries.map { it.pixels })
    }

    @Test
    fun `videos are mpeg latest_size_channel mp4 for every size and channel`() {
        for (channel in SunChannel.entries) {
            for (size in SdoImageSize.entries) {
                assertEquals(
                    "https://sdo.gsfc.nasa.gov/assets/img/latest/mpeg/" +
                        "latest_${size.pixels}_${codes.getValue(channel)}.mp4",
                    imagery.videoUrl(channel, size),
                )
            }
        }
    }

    @Test
    fun `a past moment is Helioviewer's whole-disc picture in AIA 131 at whole seconds`() {
        assertEquals(
            "https://api.helioviewer.org/v2/takeScreenshot/?date=2026-10-20T11:59:45Z&imageScale=2.4" +
                "&layers=%5B9,1,100%5D&x0=0&y0=0&width=1024&height=1024&display=true&watermark=false",
            imagery.helioviewerImageUrl(Instant.parse("2026-10-20T11:59:45.678Z")),
        )
        assertEquals(
            "https://api.helioviewer.org/v2/takeScreenshot/?date=2026-10-20T12:00:00Z&imageScale=1.2" +
                "&layers=%5B9,1,100%5D&x0=0&y0=0&width=2048&height=2048&display=true&watermark=false",
            imagery.helioviewerImageUrl(Instant.parse("2026-10-20T12:00:00Z"), pixels = 2048),
        )
    }
}
