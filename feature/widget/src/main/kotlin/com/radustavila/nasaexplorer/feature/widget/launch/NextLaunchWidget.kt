package com.radustavila.nasaexplorer.feature.widget.launch

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.designsystem.util.deepLinkIntent
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveUpcomingLaunchesUseCase
import com.radustavila.nasaexplorer.core.model.TodayDeepLink
import com.radustavila.nasaexplorer.feature.widget.R
import com.radustavila.nasaexplorer.feature.widget.WidgetFormat
import com.radustavila.nasaexplorer.feature.widget.WidgetMessage
import com.radustavila.nasaexplorer.feature.widget.WidgetMessageState
import com.radustavila.nasaexplorer.feature.widget.WidgetSizes
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * Home screen widget with the next rocket launch from the launches cache. Tapping it opens the
 * Today tab, whose card lists the next launches.
 *
 * Glance re-renders only on request, so the countdown cannot tick from here: inside the last day
 * the widget embeds a chronometer the launcher ticks by itself, and otherwise shows minutes or
 * hours to go. The launches sync re-renders it whenever new data lands (see
 * `SynchronizeUpcomingLaunchesUseCase`), and the provider info's hourly update moves the coarse
 * text along from the cache, without touching the network.
 */
class NextLaunchWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetSizes.all)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            NextLaunchWidgetEntryPoint::class.java,
        )

        val state = nextLaunchWidgetState(
            launches = entryPoint.observeUpcomingLaunches().invoke().first(),
            now = entryPoint.timeProvider().now(),
            format = WidgetFormat(context.resources),
            resources = context.resources,
        )

        val openIntent = context.deepLinkIntent(TodayDeepLink.URI)
        val title = context.getString(R.string.next_launch_widget_title)
        val emptyState = WidgetMessageState(context.getString(R.string.next_launch_widget_empty), openIntent)

        provideContent {
            if (state != null) NextLaunchWidgetContent(state, title, openIntent) else WidgetMessage(emptyState)
        }
    }
}

class NextLaunchWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextLaunchWidget()
}

/** What the widget needs from the dependency graph; widgets cannot use constructor injection. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NextLaunchWidgetEntryPoint {
    fun observeUpcomingLaunches(): ObserveUpcomingLaunchesUseCase
    fun timeProvider(): TimeProvider
}
