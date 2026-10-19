package com.radustavila.nasaexplorer.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Addresses are copied from real explanations, which use all three forms. */
class ApodLinkTest {

    @Test
    fun `a classic archive page points to its day`() {
        assertEquals(
            LocalDate.of(1996, 10, 30),
            ApodLink.apodDateOf("https://apod.nasa.gov/apod/ap961030.html"),
        )
        assertEquals(
            LocalDate.of(2008, 11, 16),
            ApodLink.apodDateOf("http://apod.nasa.gov/apod/ap081116.html"),
        )
    }

    @Test
    fun `a science dot nasa dot gov archive query points to its day`() {
        assertEquals(
            LocalDate.of(2011, 3, 3),
            ApodLink.apodDateOf("https://science.nasa.gov/apod/?date=110303"),
        )
    }

    @Test
    fun `an image article points to the day in its slug`() {
        assertEquals(
            LocalDate.of(2024, 10, 22),
            ApodLink.apodDateOf(
                "https://science.nasa.gov/image-article/apod-2024-october-22-m16-pillars-of-star-creation/",
            ),
        )
        assertEquals(
            LocalDate.of(2003, 7, 6),
            ApodLink.apodDateOf("https://science.nasa.gov/image-article/apod-2003-july-6-fractal-dust/"),
        )
    }

    @Test
    fun `other pages are not archive entries`() {
        assertNull(ApodLink.apodDateOf("https://en.wikipedia.org/wiki/Eagle_Nebula"))
        assertNull(ApodLink.apodDateOf("https://science.nasa.gov/mission/hubble/overview/about-hubble/"))
        assertNull(ApodLink.apodDateOf("https://apod.nasa.gov/apod/open_clusters.html"))
        assertNull(ApodLink.apodDateOf("not a url"))
    }

    @Test
    fun `impossible dates and dates before the archive are not entries`() {
        assertNull(ApodLink.apodDateOf("https://apod.nasa.gov/apod/ap260231.html"))
        assertNull(ApodLink.apodDateOf("https://apod.nasa.gov/apod/ap950615.html"))
        assertEquals(
            Apod.FIRST_PUBLICATION_DATE,
            ApodLink.apodDateOf("https://apod.nasa.gov/apod/ap950616.html"),
        )
    }
}
