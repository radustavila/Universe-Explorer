package com.radustavila.nasaexplorer.data.epic.prefetch

import androidx.test.core.app.ApplicationProvider
import coil3.request.CachePolicy
import coil3.size.Size
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CoilImagePrefetcherTest {

    @Test
    fun `a prefetch decodes a thumbnail, keyed like the screen's request, and skips the memory cache`() = runTest {
        val url = "https://epic.gsfc.nasa.gov/archive/natural/2026/10/19/png/epicimage.png"

        val request = CoilImagePrefetcher.prefetchRequest(ApplicationProvider.getApplicationContext(), url)

        assertEquals(url, request.data)
        assertEquals(Size(CoilImagePrefetcher.DECODE_SIZE_PX, CoilImagePrefetcher.DECODE_SIZE_PX), request.sizeResolver.size())
        assertEquals(CachePolicy.DISABLED, request.memoryCachePolicy)
        assertEquals(CachePolicy.ENABLED, request.diskCachePolicy)
    }
}
