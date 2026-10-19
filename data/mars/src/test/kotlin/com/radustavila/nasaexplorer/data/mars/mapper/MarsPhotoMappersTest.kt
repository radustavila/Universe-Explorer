package com.radustavila.nasaexplorer.data.mars.mapper

import com.radustavila.nasaexplorer.core.model.MarsRover
import com.radustavila.nasaexplorer.data.mars.remote.curiosityFixture
import com.radustavila.nasaexplorer.data.mars.remote.dto.CuriosityImageDto
import com.radustavila.nasaexplorer.data.mars.remote.dto.PerseveranceCameraDto
import com.radustavila.nasaexplorer.data.mars.remote.dto.PerseveranceImageDto
import com.radustavila.nasaexplorer.data.mars.remote.dto.PerseveranceImageFilesDto
import com.radustavila.nasaexplorer.data.mars.remote.perseveranceFixture
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarsPhotoMappersTest {

    @Test
    fun `Curiosity thumbnails are dropped`() {
        val photos = curiosityFixture().items.mapNotNull { it.toDomainOrNull() }

        assertEquals(8, photos.size)
        assertTrue(photos.none { it.id.contains("I01_") || it.id.contains("_T") })
    }

    @Test
    fun `a Curiosity image maps its camera, time, picture and caption`() {
        val photo = curiosityFixture().items.first().toDomainOrNull()!!

        assertEquals("5048MH0009060011804545C00_DXXX", photo.id)
        assertEquals(MarsRover.CURIOSITY, photo.rover)
        assertEquals(5048, photo.sol)
        assertEquals("MAHLI", photo.cameraId)
        assertEquals("Mars Hand Lens Imager (MAHLI)", photo.cameraName)
        assertEquals(Instant.parse("2026-10-18T20:22:12Z"), photo.takenAt)
        assertEquals(
            "https://mars.nasa.gov/msl-raw-images/msss/05048/mhli/5048MH0009060011804545C00_DXXX.jpg",
            photo.imageUrl,
        )
        assertNull(photo.thumbnailUrl)
        assertEquals("NASA/JPL-Caltech/MSSS", photo.credit)
        // The caption's <br /> breaks become paragraphs.
        assertFalse(photo.description!!.contains("<br"))
        assertTrue(photo.description!!.contains("\n\n"))
        assertEquals("Curiosity, sol 5048: Mars Hand Lens Imager (MAHLI)", photo.title)
    }

    @Test
    fun `Perseverance renditions are picked by size`() {
        val photos = perseveranceFixture().images.mapNotNull { it.toDomainOrNull() }

        assertEquals(6, photos.size)
        val navcam = photos.first()
        assertEquals(MarsRover.PERSEVERANCE, navcam.rover)
        assertEquals(2013, navcam.sol)
        assertEquals("NAVCAM_LEFT", navcam.cameraId)
        assertEquals("Left Navigation Camera (Navcam)", navcam.cameraName)
        // The feed gives UTC times without an offset.
        assertEquals(Instant.parse("2026-10-19T01:41:29.709Z"), navcam.takenAt)
        assertTrue(navcam.imageUrl.endsWith("_1200.jpg"))
        assertTrue(navcam.thumbnailUrl!!.endsWith("_320.jpg"))
        assertEquals(navcam.thumbnailUrl, navcam.previewUrl)
    }

    @Test
    fun `Perseverance thumbnails are dropped`() {
        assertNull(perseverance(sampleType = "Thumbnail").toDomainOrNull())
    }

    @Test
    fun `a UTC time without an offset is read as UTC`() {
        val photo = perseverance(dateTakenUtc = "2026-10-17T21:05:12.000").toDomainOrNull()!!

        assertEquals(Instant.parse("2026-10-17T21:05:12Z"), photo.takenAt)
    }

    @Test
    fun `entries missing an id, a sol, a camera or a picture are dropped`() {
        assertNull(perseverance(imageid = " ").toDomainOrNull())
        assertNull(perseverance(sol = null).toDomainOrNull())
        assertNull(perseverance(instrument = null).toDomainOrNull())
        assertNull(perseverance(files = PerseveranceImageFilesDto()).toDomainOrNull())
        assertNull(CuriosityImageDto(imageid = "x", sol = 1, instrument = "MAHLI").toDomainOrNull())
    }

    @Test
    fun `a title without a camera part falls back to the camera code`() {
        val photo = CuriosityImageDto(
            imageid = "x",
            sol = 1,
            instrument = "NAV_LEFT_B",
            title = "Untitled",
            url = "http://mars.nasa.gov/x.jpg",
        ).toDomainOrNull()!!

        assertEquals("NAV LEFT B", photo.cameraName)
        assertEquals("https://mars.nasa.gov/x.jpg", photo.imageUrl)
    }

    @Test
    fun `the domain model survives a round trip through Room`() {
        val photo = perseveranceFixture().images.first().toDomainOrNull()!!

        assertEquals(photo, photo.toEntity(Instant.EPOCH).toDomain())
    }

    @Test
    fun `html captions become plain text`() {
        assertEquals("A & B\n\nC \"quoted\"", "A &amp; B<br /><br/><b>C</b> &quot;quoted&quot;".htmlToText())
    }

    private fun perseverance(
        imageid: String? = "ID",
        sol: Int? = 1,
        instrument: String? = "NAVCAM_LEFT",
        dateTakenUtc: String? = null,
        sampleType: String = "Full",
        files: PerseveranceImageFilesDto = PerseveranceImageFilesDto(
            large = "https://m/l.jpg",
            small = "https://m/s.jpg",
        ),
    ) = PerseveranceImageDto(
        imageid = imageid,
        sol = sol,
        camera = PerseveranceCameraDto(instrument = instrument),
        title = "Mars Perseverance Sol 1: Left Navigation Camera (Navcam)",
        sampleType = sampleType,
        dateTakenUtc = dateTakenUtc,
        imageFiles = files,
    )
}
