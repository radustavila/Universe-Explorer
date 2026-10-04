package com.radustavila.nasaexplorer.data.nasa.remote.dto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Fixtures are trimmed copies of real science.nasa.gov `apod-basic` entries. */
class ApodBasicDtoTest {

    @Test
    fun `an image takes its picture from the page and keeps hdurl as the high resolution copy`() {
        val dto = ApodBasicDto(
            date = "2026-10-04",
            mediaType = "image",
            hdUrl = "https://assets.science.nasa.gov/rainbows.jpg?w=1362&h=1920",
            basicHtml = """
                <a href="https://science.nasa.gov/apod/archive/">Discover the cosmos!</a>
                <a href="https://assets.science.nasa.gov/rainbows.jpg">
                <IMG SRC="https://assets.science.nasa.gov/rainbows.jpg" alt="Rainbows"></a>
            """.trimIndent(),
        ).toApodDto()

        assertEquals("https://assets.science.nasa.gov/rainbows.jpg", dto.url)
        assertEquals("https://assets.science.nasa.gov/rainbows.jpg?w=1362&h=1920", dto.hdUrl)
        assertNull(dto.thumbnailUrl)
    }

    @Test
    fun `a hosted video plays its file and uses hdurl as its still frame`() {
        val dto = ApodBasicDto(
            mediaType = "video",
            hdUrl = "https://assets.science.nasa.gov/Comet_snapshot.png",
            basicHtml = """
                <video controls><source src="https://assets.science.nasa.gov/Neowise.mp4" type="video/mp4"></video>
            """.trimIndent(),
        ).toApodDto()

        assertEquals("https://assets.science.nasa.gov/Neowise.mp4", dto.url)
        assertEquals("https://assets.science.nasa.gov/Comet_snapshot.png", dto.thumbnailUrl)
        assertNull(dto.hdUrl)
    }

    @Test
    fun `protocol relative embeds become https and the generic placeholder image is ignored`() {
        val dto = ApodBasicDto(
            mediaType = "video",
            hdUrl = PLACEHOLDER,
            basicHtml = """<iframe width="960" src="//www.youtube.com/embed/ts0Ek3nLHew?rel=0"></iframe>""",
        ).toApodDto()

        assertEquals("https://www.youtube.com/embed/ts0Ek3nLHew?rel=0", dto.url)
        assertNull(dto.thumbnailUrl)
    }

    @Test
    fun `an old entry whose hdurl is the placeholder has no high resolution copy`() {
        val dto = ApodBasicDto(
            mediaType = "image",
            hdUrl = PLACEHOLDER,
            basicHtml = """<IMG SRC="https://assets.science.nasa.gov/1995/june/jupiter1_vgr.gif">""",
        ).toApodDto()

        assertEquals("https://assets.science.nasa.gov/1995/june/jupiter1_vgr.gif", dto.url)
        assertNull(dto.hdUrl)
    }

    @Test
    fun `explanation loses its label, markup and trailing site notices`() {
        val dto = ApodBasicDto(
            explanation = "<strong>Explanation: </strong>Yes, but can your <a href=\"x\">rainbow</a> " +
                "do this? It&#039;s <em>only</em> waves &amp; light." +
                "<br><br><strong>Your Sky Surprise: </strong>What picture?<br>" +
                "<strong>Tomorrow's picture: </strong>open space",
        ).toApodDto()

        assertEquals("Yes, but can your rainbow do this? It's only waves & light.", dto.explanation)
    }

    @Test
    fun `explanation keeps a further reading paragraph that is not a notice`() {
        val dto = ApodBasicDto(
            explanation = "Astronomers have observed this storm.<br /><br />For more information see " +
                "<a href=\"x\" target=\"_blank\"> NASA JPL release. </a>",
        ).toApodDto()

        assertEquals(
            "Astronomers have observed this storm. For more information see NASA JPL release.",
            dto.explanation,
        )
    }

    @Test
    fun `credit loses its label and the spacing left behind by links`() {
        val dto = ApodBasicDto(
            copyright = "<b> Credit: </b> <a href=\"x\">Peter Tuthill</a> ( <a href=\"y\">Sydney University</a> )" +
                " et al., W.M. Keck Observatory",
        ).toApodDto()

        assertEquals("Peter Tuthill (Sydney University) et al., W.M. Keck Observatory", dto.copyright)
    }

    @Test
    fun `credit labels mentioning copyright are stripped too and plain credits are kept`() {
        assertEquals(
            "Paolo Girotti",
            ApodBasicDto(copyright = "<b> Video Credit &amp; Copyright: </b> <a href=\"x\">Paolo Girotti</a>")
                .toApodDto().copyright,
        )
        assertEquals(
            "NASA, JPL, NSSDC, Voyager",
            ApodBasicDto(copyright = "NASA, JPL, NSSDC, Voyager").toApodDto().copyright,
        )
    }

    @Test
    fun `an entry without a page has no media url`() {
        assertNull(ApodBasicDto(date = "2026-10-04", mediaType = "image").toApodDto().url)
    }

    private companion object {
        const val PLACEHOLDER = "https://assets.science.nasa.gov/dynamicimage/assets/science/astro/programs/" +
            "cosmic-origins/images/misc/news-thumbnail.png?w=594&h=516"
    }
}
