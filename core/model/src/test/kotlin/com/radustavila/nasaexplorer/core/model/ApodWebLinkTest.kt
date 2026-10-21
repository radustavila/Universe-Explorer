package com.radustavila.nasaexplorer.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApodWebLinkTest {

    private val today = LocalDate.of(2026, 10, 21)

    @Test
    fun `an archive page opens its day`() {
        assertEquals(LocalDate.of(2024, 1, 1), ApodWebLink.dateOf("https://apod.nasa.gov/apod/ap240101.html", today))
        assertEquals(LocalDate.of(1999, 12, 31), ApodWebLink.dateOf("http://apod.nasa.gov/apod/ap991231.html", today))
        assertEquals(LocalDate.of(2024, 1, 1), ApodWebLink.dateOf("https://www.apod.nasa.gov/apod/AP240101.HTML", today))
    }

    @Test
    fun `the today page opens today`() {
        assertEquals(today, ApodWebLink.dateOf("https://apod.nasa.gov/apod/astropix.html", today))
    }

    @Test
    fun `other pages, impossible days and other sites are left to the browser`() {
        listOf(
            "https://apod.nasa.gov/apod/archivepix.html",
            "https://apod.nasa.gov/apod/ap_index.html",
            "https://apod.nasa.gov/apod/ap2401011.html",
            "https://apod.nasa.gov/apod/ap240231.html",
            "https://apod.nasa.gov/apod/ap950615.html",
            "https://apod.nasa.gov/apod/lib/apsubmit.html",
            "https://science.nasa.gov/apod/?date=240101",
            "https://example.com/apod/ap240101.html",
            "ftp://apod.nasa.gov/apod/ap240101.html",
            "not a url",
        ).forEach { url -> assertNull(url, ApodWebLink.dateOf(url, today)) }
    }
}
