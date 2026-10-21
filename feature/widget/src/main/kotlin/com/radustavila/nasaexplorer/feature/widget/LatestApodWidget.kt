package com.radustavila.nasaexplorer.feature.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import com.radustavila.nasaexplorer.core.designsystem.util.apodDetailsIntent
import com.radustavila.nasaexplorer.core.designsystem.util.formatMedium
import com.radustavila.nasaexplorer.core.designsystem.util.loadBitmap
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveLatestApodUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RefreshFeedUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * Home screen widget showing the most recent Astronomy Picture of the Day from the local cache.
 *
 * Everything is resolved before composition - the widget is rendered into RemoteViews in another
 * process, so the picture has to be a ready bitmap rather than something loaded while drawing. The
 * widget never fetches from the network itself: with an empty cache it asks the sync pipeline for
 * data, and the sync worker re-renders it once the data has landed.
 */
class LatestApodWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            LatestApodWidgetEntryPoint::class.java,
        )

        val latest = entryPoint.observeLatestApod().invoke().first()
        if (latest == null) entryPoint.refreshFeed().invoke()

        val state = latest?.let { apod ->
            LatestApodWidgetState(
                title = apod.title,
                pictureDescription = apod.imageDescription,
                dateLabel = apod.date.formatMedium(),
                picture = apod.previewImageUrl?.let { url ->
                    val sizePx = context.widgetPictureSizePx(
                        id,
                        cropped = true,
                        minPx = MIN_PICTURE_PX,
                        maxPx = MAX_PICTURE_PX,
                    )
                    context.loadBitmap(url, sizePx, opaque = true)
                },
                openIntent = context.apodDetailsIntent(apod.date),
            )
        }
        val emptyState = WidgetMessageState(
            message = context.getString(R.string.latest_apod_widget_empty),
            openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName),
        )

        provideContent {
            if (state != null) LatestApodContent(state) else WidgetMessage(emptyState)
        }
    }

    private companion object {
        /**
         * The picture is decoded for the widget's size, as RGB_565 (photos have no transparency):
         * at most 512 px is half a megabyte, sharp enough for a large widget and well inside the
         * binder budget. The old fixed 720 px ARGB picture was two megabytes.
         */
        const val MIN_PICTURE_PX = 160
        const val MAX_PICTURE_PX = 512
    }
}

/** What the widget needs from the dependency graph; widgets cannot use constructor injection. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface LatestApodWidgetEntryPoint {
    fun observeLatestApod(): ObserveLatestApodUseCase
    fun refreshFeed(): RefreshFeedUseCase
}

private class LatestApodWidgetState(
    val title: String,
    val pictureDescription: String,
    val dateLabel: String,
    val picture: Bitmap?,
    val openIntent: Intent?,
)

@Composable
private fun LatestApodContent(state: LatestApodWidgetState) {
    Box(
        modifier = widgetFrame(state.openIntent),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (state.picture != null) {
            Image(
                provider = ImageProvider(state.picture),
                contentDescription = state.pictureDescription,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize(),
            )
        }

        // A scrim under the caption keeps it legible over bright pictures.
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(WidgetColors.Scrim)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = state.dateLabel,
                maxLines = 1,
                style = widgetText(color = WidgetColors.SecondaryText, fontSize = 12.sp),
            )
            Text(
                text = state.title,
                maxLines = 2,
                style = widgetText(fontSize = 15.sp, bold = true),
            )
        }
    }
}
