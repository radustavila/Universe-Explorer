package com.radustavila.nasaexplorer.data.spaceweather.sun

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.radustavila.nasaexplorer.core.common.error.AppError
import com.radustavila.nasaexplorer.core.common.result.DataResult
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.repository.SunImageRepository
import com.radustavila.nasaexplorer.core.model.SunChannel
import com.radustavila.nasaexplorer.core.model.SunImage
import com.radustavila.nasaexplorer.core.network.http.safeNetworkCall
import com.radustavila.nasaexplorer.data.spaceweather.di.SunPreferencesStore
import com.radustavila.nasaexplorer.data.spaceweather.remote.SdoService
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import retrofit2.HttpException

/**
 * SDO's latest pictures kept in [SunImageFiles], the chosen channel in its own DataStore (a user
 * choice, backed up), and the URLs of what is streamed: the 48-hour videos and Helioviewer's past
 * pictures.
 */
@Singleton
class SdoSunImageRepository @Inject constructor(
    private val service: SdoService,
    private val files: SunImageFiles,
    private val imagery: SdoImagery,
    @SunPreferencesStore private val dataStore: DataStore<Preferences>,
    private val timeProvider: TimeProvider,
) : SunImageRepository {

    override val selectedChannel: Flow<SunChannel> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            val code = preferences[CHANNEL]
            SunChannel.entries.firstOrNull { it.sdoCode == code } ?: SunChannel.VISIBLE_LIGHT
        }
        .distinctUntilChanged()

    override suspend fun selectChannel(channel: SunChannel) {
        try {
            dataStore.edit { it[CHANNEL] = channel.sdoCode }
        } catch (_: IOException) {
            // The card keeps the previous channel; the user can pick again.
        }
    }

    override fun observeLatestImage(channel: SunChannel): Flow<SunImage?> =
        files.images.map { images -> images[channel]?.toDomain() }.distinctUntilChanged()

    override suspend fun refreshLatestImage(channel: SunChannel): DataResult<Unit> =
        when (val result = safeNetworkCall { download(channel) }) {
            is DataResult.Success ->
                if (result.data) DataResult.Success(Unit) else DataResult.Failure(AppError.EmptyResponse)
            is DataResult.Failure -> result
        }

    /** @return false when SDO answered without a picture. */
    private suspend fun download(channel: SunChannel): Boolean {
        val current = files.current(channel)
        val response = service.latestImage(
            file = imagery.stillFileName(channel, CARD_SIZE),
            ifModifiedSince = current?.updatedAt?.let(HTTP_DATE::format),
        )
        // 304: the picture on the device is still SDO's latest.
        if (response.code() == HTTP_NOT_MODIFIED || !response.isSuccessful) {
            response.errorBody()?.close()
            if (response.code() == HTTP_NOT_MODIFIED) return true
            throw HttpException(response)
        }
        return response.body()?.use { body ->
            body.contentLength() != 0L && run {
                val updatedAt = response.headers().getDate(LAST_MODIFIED)?.toInstant() ?: timeProvider.now()
                files.save(channel, updatedAt, body.byteStream())
                true
            }
        } ?: false
    }

    override fun videoUrl(channel: SunChannel): String = imagery.videoUrl(channel, VIDEO_SIZE)

    override fun imageUrlAt(time: Instant): String = imagery.helioviewerImageUrl(time)

    private fun StoredSunImage.toDomain() = SunImage(
        channel = channel,
        localUrl = Uri.fromFile(file).toString(),
        // A new query per picture, so the image loader never shows a cached older one at this URL.
        fullSizeUrl = "${imagery.stillUrl(channel, FULL_SIZE)}?v=${updatedAt.epochSecond}",
        updatedAt = updatedAt,
    )

    private companion object {
        val CHANNEL = stringPreferencesKey("channel")
        val CARD_SIZE = SdoImageSize.MEDIUM
        val FULL_SIZE = SdoImageSize.LARGE
        val VIDEO_SIZE = SdoImageSize.SMALL
        const val HTTP_NOT_MODIFIED = 304
        const val LAST_MODIFIED = "Last-Modified"
        val HTTP_DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC)
    }
}
