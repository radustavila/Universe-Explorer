package com.radustavila.nasaexplorer.data.nasaimages.remote

import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.model.GalleryRail
import com.radustavila.nasaexplorer.core.model.NasaMediaQuery
import com.radustavila.nasaexplorer.core.model.NasaMediaType
import com.radustavila.nasaexplorer.core.testing.http.FixtureServer
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class NasaImagesRemoteDataSourceTest {

    @get:Rule
    val server = FixtureServer()

    private lateinit var remoteDataSource: NasaImagesRemoteDataSource

    @Before
    fun setUp() {
        remoteDataSource = RetrofitNasaImagesRemoteDataSource(nasaImagesService(server), UnconfinedTestDispatcher())
    }

    @Test
    fun `a rail asks for images matching its query and telescope keyword`() = runTest {
        server.enqueueFixture(NasaImagesFixtures.WEBB_GALAXIES)

        val result = remoteDataSource.fetchRail(GalleryRail.WEBB_GALAXIES)

        assertEquals(5, (result as DataResult.Success).data.collection.items.size)
        val url = server.takeRequest().requestUrl!!
        assertEquals("/search", url.encodedPath)
        assertEquals("galaxy", url.queryParameter("q"))
        assertEquals("James Webb Space Telescope", url.queryParameter("keywords"))
        assertEquals("image", url.queryParameter("media_type"))
        assertEquals("${NasaImagesRemoteDataSource.RAIL_SIZE}", url.queryParameter("page_size"))
    }

    @Test
    fun `a rail without a keyword sends none`() = runTest {
        server.enqueueFixture(NasaImagesFixtures.WEBB_GALAXIES)

        remoteDataSource.fetchRail(GalleryRail.BLACK_HOLES)

        val url = server.takeRequest().requestUrl!!
        assertEquals("black hole", url.queryParameter("q"))
        assertNull(url.queryParameter("keywords"))
    }

    @Test
    fun `a search sends its text, media types, years and page`() = runTest {
        server.enqueueFixture(NasaImagesFixtures.MIXED_PAGE_2)

        remoteDataSource.search(
            NasaMediaQuery(
                text = "webb",
                mediaTypes = setOf(NasaMediaType.AUDIO, NasaMediaType.IMAGE),
                yearStart = 2000,
                yearEnd = 2024,
            ),
            page = 2,
        )

        val url = server.takeRequest().requestUrl!!
        assertEquals("webb", url.queryParameter("q"))
        assertEquals("image,audio", url.queryParameter("media_type"))
        assertEquals("2000", url.queryParameter("year_start"))
        assertEquals("2024", url.queryParameter("year_end"))
        assertEquals("2", url.queryParameter("page"))
        assertEquals("${NasaImagesRemoteDataSource.SEARCH_PAGE_SIZE}", url.queryParameter("page_size"))
    }

    @Test
    fun `a search without years leaves them out`() = runTest {
        server.enqueueFixture(NasaImagesFixtures.EMPTY)

        remoteDataSource.search(NasaMediaQuery(text = "zzz"), page = 1)

        val url = server.takeRequest().requestUrl!!
        assertEquals("image,video,audio", url.queryParameter("media_type"))
        assertNull(url.queryParameter("year_start"))
        assertNull(url.queryParameter("year_end"))
    }

    @Test
    fun `ids with spaces are percent-encoded in the asset path`() = runTest {
        server.enqueueFixture(NasaImagesFixtures.VIDEO_ASSETS)

        val result = remoteDataSource.fetchAssets("Webb Journey to Space Ep2")

        assertTrue(result is DataResult.Success)
        assertEquals("/asset/Webb%20Journey%20to%20Space%20Ep2", server.takeRequest().requestUrl!!.encodedPath)
    }

    @Test
    fun `an item is looked up by id through search`() = runTest {
        server.enqueueFixture(NasaImagesFixtures.BY_ID)

        remoteDataSource.findById("PIA26702")

        val url = server.takeRequest().requestUrl!!
        assertEquals("/search", url.encodedPath)
        assertEquals("PIA26702", url.queryParameter("nasa_id"))
    }

    @Test
    fun `an unknown asset is an HTTP 404`() = runTest {
        server.enqueueJson("""{"reason":"No AssetDB records for nasaid=x"}""", code = 404)

        val result = remoteDataSource.fetchAssets("x")

        assertEquals(404, ((result as DataResult.Failure).error as AppError.Http).code)
    }

    @Test
    fun `paging past the library's limit is an HTTP 400`() = runTest {
        server.enqueueJson("""{"reason":"Maximum number of search results have been displayed."}""", code = 400)

        val result = remoteDataSource.search(NasaMediaQuery(text = "galaxy"), page = 400)

        assertEquals(400, ((result as DataResult.Failure).error as AppError.Http).code)
    }
}
