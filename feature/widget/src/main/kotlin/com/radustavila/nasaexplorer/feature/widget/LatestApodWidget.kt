package com.radustavila.nasaexplorer.feature.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
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
                picture = apod.previewImageUrl?.let { url -> context.loadBitmap(url, PICTURE_SIZE_PX) },
                openIntent = context.apodDetailsIntent(apod.date),
            )
        }
        val emptyState = LatestApodWidgetEmptyState(
            message = context.getString(R.string.latest_apod_widget_empty),
            openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName),
        )

        provideContent {
            if (state != null) LatestApodContent(state) else EmptyContent(emptyState)
        }
    }

    private companion object {
        /** Sharp on a large widget, and comfortably under the RemoteViews bitmap budget. */
        const val PICTURE_SIZE_PX = 720
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

private class LatestApodWidgetEmptyState(
    val message: String,
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
                .background(ScrimColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = state.dateLabel,
                maxLines = 1,
                style = TextStyle(color = ColorProvider(SecondaryTextColor), fontSize = 12.sp),
            )
            Text(
                text = state.title,
                maxLines = 2,
                style = TextStyle(
                    color = ColorProvider(PrimaryTextColor),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

@Composable
private fun EmptyContent(state: LatestApodWidgetEmptyState) {
    Box(
        modifier = widgetFrame(state.openIntent).padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = state.message,
            style = TextStyle(
                color = ColorProvider(PrimaryTextColor),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

private fun widgetFrame(openIntent: Intent?): GlanceModifier {
    val frame = GlanceModifier
        .fillMaxSize()
        .cornerRadius(16.dp)
        .background(BackgroundColor)
    return if (openIntent != null) frame.clickable(actionStartActivity(openIntent)) else frame
}

// The app is always dark, so the widget is too, whatever the launcher's theme.
private val BackgroundColor = Color(0xFF07070D)
private val ScrimColor = Color(0x99000000)
private val PrimaryTextColor = Color(0xFFFFFFFF)
private val SecondaryTextColor = Color(0xFFB8C0D9)
