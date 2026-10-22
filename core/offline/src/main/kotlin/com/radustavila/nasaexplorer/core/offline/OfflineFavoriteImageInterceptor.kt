package com.radustavila.nasaexplorer.core.offline

import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import javax.inject.Inject

/**
 * Serves a favourite's image from its offline copy wherever the app shows it - the Favourites tab,
 * the details screens, the widget and notifications - while every screen keeps asking for the
 * remote URL it already knows.
 *
 * Should the copy fail to load (deleted underneath the database, say), the request carries on to
 * the network and the image cache as if there were none.
 */
class OfflineFavoriteImageInterceptor @Inject constructor(
    private val index: OfflineFavoriteImageIndex,
) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val url = chain.request.data as? String ?: return chain.proceed()
        val offlineUri = index.offlineUriFor(url) ?: return chain.proceed()

        val offline = chain.withRequest(chain.request.newBuilder().data(offlineUri).build()).proceed()
        return if (offline is ErrorResult) chain.proceed() else offline
    }
}
