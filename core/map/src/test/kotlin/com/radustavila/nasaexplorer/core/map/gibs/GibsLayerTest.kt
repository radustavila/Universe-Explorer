package com.radustavila.nasaexplorer.core.map.gibs

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GibsLayerTest {

    private val day = LocalDate.of(2026, 10, 18)
    private val base = "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best"

    @Test
    fun `VIIRS Suomi NPP true colour is a daily level 9 jpg`() {
        assertEquals(
            "$base/VIIRS_SNPP_CorrectedReflectance_TrueColor/default/2026-10-18/GoogleMapsCompatible_Level9/{z}/{y}/{x}.jpg",
            GibsLayer.ViirsSnppTrueColor.tileUrlTemplate(day),
        )
    }

    @Test
    fun `VIIRS NOAA-20 true colour is a daily level 9 jpg`() {
        assertEquals(
            "$base/VIIRS_NOAA20_CorrectedReflectance_TrueColor/default/2026-10-18/GoogleMapsCompatible_Level9/3/2/5.jpg",
            GibsLayer.ViirsNoaa20TrueColor.tileUrl(day, zoom = 3, x = 5, y = 2),
        )
    }

    @Test
    fun `MODIS Terra true colour is a daily level 9 jpg`() {
        assertEquals(
            "$base/MODIS_Terra_CorrectedReflectance_TrueColor/default/2015-01-01/GoogleMapsCompatible_Level9/2/1/1.jpg",
            GibsLayer.ModisTerraTrueColor.tileUrl(LocalDate.of(2015, 1, 1), zoom = 2, x = 1, y = 1),
        )
    }

    @Test
    fun `Black Marble always uses its fixed 2016 date and level 8 png`() {
        val expected = "$base/VIIRS_Black_Marble/default/2016-01-01/GoogleMapsCompatible_Level8/{z}/{y}/{x}.png"
        assertEquals(expected, GibsLayer.ViirsBlackMarble.tileUrlTemplate(day))
        assertEquals(expected, GibsLayer.ViirsBlackMarble.tileUrlTemplate(null))
    }

    @Test
    fun `Blue Marble has no date segment and is a level 8 jpeg`() {
        val expected = "$base/BlueMarble_ShadedRelief_Bathymetry/default/GoogleMapsCompatible_Level8/{z}/{y}/{x}.jpeg"
        assertEquals(expected, GibsLayer.BlueMarble.tileUrlTemplate(day))
        assertEquals(expected, GibsLayer.BlueMarble.tileUrlTemplate(null))
    }

    @Test
    fun `max zoom follows the tile matrix set`() {
        assertEquals(9, GibsLayer.ViirsSnppTrueColor.maxZoom)
        assertEquals(8, GibsLayer.ViirsBlackMarble.maxZoom)
        assertEquals(8, GibsLayer.BlueMarble.maxZoom)
    }

    @Test
    fun `a daily layer without a date is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            GibsLayer.ViirsSnppTrueColor.tileUrlTemplate(null)
        }
    }

    @Test
    fun `layers from other sources can be described, e g one EONET lists`() {
        val fires = GibsLayer(
            id = "MODIS_Terra_Thermal_Anomalies_Day",
            matrixSet = requireNotNull(GibsTileMatrixSet.fromId("GoogleMapsCompatible_Level7")),
            format = "png",
            time = GibsTime.Daily(),
        )

        assertEquals(
            "$base/MODIS_Terra_Thermal_Anomalies_Day/default/2026-10-18/GoogleMapsCompatible_Level7/{z}/{y}/{x}.png",
            fires.tileUrlTemplate(day),
        )
    }

    @Test
    fun `true colour comes from Suomi NPP from its first day and from Terra before`() {
        assertEquals(GibsLayer.ViirsSnppTrueColor, GibsLayer.trueColorFor(LocalDate.of(2015, 11, 24)))
        assertEquals(GibsLayer.ViirsSnppTrueColor, GibsLayer.trueColorFor(day))
        assertEquals(GibsLayer.ModisTerraTrueColor, GibsLayer.trueColorFor(LocalDate.of(2015, 11, 23)))
        assertEquals(GibsLayer.ModisTerraTrueColor, GibsLayer.trueColorFor(LocalDate.of(2015, 1, 1)))
    }

    @Test
    fun `NOAA-20 fills the true colour gaps from its first day, Aqua before`() {
        assertEquals(GibsLayer.ViirsNoaa20TrueColor, GibsLayer.trueColorGapFillFor(LocalDate.of(2018, 1, 1)))
        assertEquals(GibsLayer.ModisAquaTrueColor, GibsLayer.trueColorGapFillFor(LocalDate.of(2017, 12, 31)))

        val imagery = GibsImagery.trueColor(day)
        assertEquals(listOf(GibsLayer.ViirsNoaa20TrueColor, GibsLayer.ViirsSnppTrueColor), imagery.layers)
        assertEquals(listOf(GibsLayer.BlueMarble), GibsImagery(GibsLayer.BlueMarble).layers)
    }

    @Test
    fun `daily corrected reflectance mosaics have no-data gaps, composites do not`() {
        assertTrue(GibsLayer.ViirsSnppTrueColor.hasNoDataGaps)
        assertTrue(GibsLayer.ModisAquaTrueColor.hasNoDataGaps)
        assertFalse(GibsLayer.BlueMarble.hasNoDataGaps)
        assertFalse(GibsLayer.ViirsBlackMarble.hasNoDataGaps)
    }
}
