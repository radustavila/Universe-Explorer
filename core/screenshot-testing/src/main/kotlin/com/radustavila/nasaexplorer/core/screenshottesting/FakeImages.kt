package com.radustavila.nasaexplorer.core.screenshottesting

import android.graphics.drawable.GradientDrawable
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult

/**
 * Makes every Coil request in the test JVM answer at once with a gradient picked from its URL, so
 * screenshots show "a picture" deterministically and never touch the network.
 */
@OptIn(DelicateCoilApi::class)
internal fun installFakeImageLoader() {
    val loader = ImageLoader.Builder(ApplicationProvider.getApplicationContext())
        .components { add(FakeImageInterceptor) }
        .build()
    SingletonImageLoader.setUnsafe(loader)
}

private object FakeImageInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val palette = PALETTES[Math.floorMod(chain.request.data.toString().hashCode(), PALETTES.size)]
        val drawable = GradientDrawable(GradientDrawable.Orientation.TL_BR, palette)
        return SuccessResult(
            image = drawable.asImage(),
            request = chain.request,
            dataSource = DataSource.MEMORY_CACHE,
        )
    }
}

/** Night-sky colours, so fake pictures look at home in the dark theme. */
private val PALETTES = listOf(
    intArrayOf(0xFF1B2A4A.toInt(), 0xFF7B5EA7.toInt()),
    intArrayOf(0xFF0E3B43.toInt(), 0xFF3FA7A0.toInt()),
    intArrayOf(0xFF3A1C32.toInt(), 0xFFD9825B.toInt()),
    intArrayOf(0xFF102A1E.toInt(), 0xFF6DBE7A.toInt()),
)
