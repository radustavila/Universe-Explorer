package com.radustavila.nasaexplorer.feature.widget.earth

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.designsystem.util.deepLinkIntent
import com.radustavila.nasaexplorer.core.designsystem.util.loadBitmap
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveLatestEpicDayUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveUnitPreferencesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshEpicUseCase
import com.radustavila.nasaexplorer.core.model.EarthTodayDeepLink
import com.radustavila.nasaexplorer.core.model.EpicImage
import com.radustavila.nasaexplorer.feature.widget.R
import com.radustavila.nasaexplorer.feature.widget.WidgetFormat
import com.radustavila.nasaexplorer.feature.widget.WidgetMessage
import com.radustavila.nasaexplorer.feature.widget.WidgetMessageState
import com.radustavila.nasaexplorer.feature.widget.widgetPictureSizePx
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * Home screen widget with the newest EPIC whole-Earth photo from the cache.
 *
 * The photo is decoded for the widget's size, as RGB_565, and never above [MAX_PICTURE_PX]: at most
 * 300 KB, far inside the binder budget. The large image is used when Coil has it or can fetch it;
 * offline, the small rotation thumbnail, which the EPIC sync prefetches, stands in. The EPIC sync
 * re-renders the widget after each successful run (see `SynchronizeEpicUseCase`); with nothing
 * cached it asks for one, like the APOD widget.
 */
class EarthTodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            EarthTodayWidgetEntryPoint::class.java,
        )

        val photo = entryPoint.observeLatestEpicDay().invoke().first().newestPhoto()
        if (photo == null) entryPoint.refreshEpic().invoke()

        val state = photo?.let {
            val format = WidgetFormat.forWidget(context, entryPoint.observeUnitPreferences())
            earthTodayWidgetState(it, entryPoint.timeProvider().now(), format, context.resources)
        }
        val picture = photo?.let {
            context.loadPicture(it, context.widgetPictureSizePx(id, cropped = false, MIN_PICTURE_PX, MAX_PICTURE_PX))
        }
        val openIntent = context.deepLinkIntent(EarthTodayDeepLink.URI)
        val emptyState = WidgetMessageState(context.getString(R.string.earth_widget_empty), openIntent)

        provideContent {
            if (state != null) EarthTodayWidgetContent(state, picture, openIntent) else WidgetMessage(emptyState)
        }
    }

    private suspend fun Context.loadPicture(photo: EpicImage, sizePx: Int) =
        if (sizePx <= THUMBNAIL_PX) {
            loadBitmap(photo.thumbnailUrl, sizePx, opaque = true)
        } else {
            loadBitmap(photo.imageUrl, sizePx, opaque = true) ?: loadBitmap(photo.thumbnailUrl, sizePx, opaque = true)
        }

    private companion object {
        const val MIN_PICTURE_PX = 120
        const val MAX_PICTURE_PX = 384

        /** EPIC's thumbnails are about this wide: enough for a small widget, and cached offline. */
        const val THUMBNAIL_PX = 120
    }
}

class EarthTodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EarthTodayWidget()
}

/** What the widget needs from the dependency graph; widgets cannot use constructor injection. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface EarthTodayWidgetEntryPoint {
    fun observeUnitPreferences(): ObserveUnitPreferencesUseCase
    fun observeLatestEpicDay(): ObserveLatestEpicDayUseCase
    fun refreshEpic(): RefreshEpicUseCase
    fun timeProvider(): TimeProvider
}
