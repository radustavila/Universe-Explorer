package com.radustavila.nasaexplorer.data.earthevents.mapper

import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import com.radustavila.nasaexplorer.core.model.EventImageryLayer
import com.radustavila.nasaexplorer.data.earthevents.remote.EonetFixtures
import com.radustavila.nasaexplorer.data.earthevents.remote.layersFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageryLayerMappersTest {

    private val wildfireLayers = layersFixture(EonetFixtures.LAYERS_WILDFIRES).toImageryLayers()

    @Test
    fun `only GIBS WMTS layers are kept, translated to Web Mercator`() {
        assertEquals(24, wildfireLayers.size)
        assertTrue(wildfireLayers.none { it.id.contains("Fires") })
        assertEquals(
            EventImageryLayer("VIIRS_SNPP_CorrectedReflectance_TrueColor", "GoogleMapsCompatible_Level9", "jpg"),
            wildfireLayers.first { it.id == "VIIRS_SNPP_CorrectedReflectance_TrueColor" },
        )
        assertEquals(
            EventImageryLayer("AIRS_CO_Total_Column_Day", "GoogleMapsCompatible_Level6", "png"),
            wildfireLayers.first(),
        )
        assertEquals(
            "GoogleMapsCompatible_Level8",
            wildfireLayers.first { it.id == "MODIS_Terra_SurfaceReflectance_Bands721" }.tileMatrixSet,
        )
    }

    @Test
    fun `true colour is preferred, from the satellite with the best coverage`() {
        assertEquals(
            "VIIRS_SNPP_CorrectedReflectance_TrueColor",
            chooseImageryLayer(EarthEventCategory.WILDFIRES, wildfireLayers)?.id,
        )
        val withoutViirs = wildfireLayers.filterNot { it.id.startsWith("VIIRS") }
        assertEquals(
            "MODIS_Terra_CorrectedReflectance_TrueColor",
            chooseImageryLayer(EarthEventCategory.WILDFIRES, withoutViirs)?.id,
        )
    }

    @Test
    fun `floods prefer the false colour that shows water`() {
        assertEquals(
            "MODIS_Terra_CorrectedReflectance_Bands721",
            chooseImageryLayer(EarthEventCategory.FLOODS, wildfireLayers)?.id,
        )
    }

    @Test
    fun `without a preferred layer the first listed one is used, and none without layers`() {
        val others = wildfireLayers.filterNot { it.id.contains("CorrectedReflectance") }

        assertEquals(others.first(), chooseImageryLayer(EarthEventCategory.WILDFIRES, others))
        assertNull(chooseImageryLayer(EarthEventCategory.WILDFIRES, emptyList()))
    }
}
