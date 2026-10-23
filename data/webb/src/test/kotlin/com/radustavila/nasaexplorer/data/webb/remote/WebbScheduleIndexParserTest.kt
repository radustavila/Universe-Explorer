package com.radustavila.nasaexplorer.data.webb.remote

import com.radustavila.nasaexplorer.core.testing.http.Fixtures
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebbScheduleIndexParserTest {

    private val pageUrl = "https://www.stsci.edu/jwst/science-execution/observing-schedules".toHttpUrl()

    @Test
    fun `schedule links are read newest first and resolved against the page`() {
        val links = WebbScheduleIndexParser.scheduleLinks(Fixtures.read("webb/observing_schedules.html"), pageUrl)

        assertEquals(9, links.size)
        assertEquals(
            "https://www.stsci.edu/files/live/sites/www/files/home/jwst/science-execution/observing-schedules/" +
                "_documents/20261019_report_20261022.txt",
            links.first().toString(),
        )
        assertEquals("20261012_report_20261008.txt", links[1].pathSegments.last())
        // The first years' files are named after their OP package.
        assertEquals("2219105f02_report_20220710.txt", links.last().pathSegments.last())
        // Other documents on the page are not schedules.
        assertTrue(links.none { it.encodedPath.endsWith(".pdf") })
    }

    @Test
    fun `a page without schedule links has none`() {
        val html = "<html><a href=\"/about\">About</a></html>"

        assertTrue(WebbScheduleIndexParser.scheduleLinks(html, pageUrl).isEmpty())
    }
}
