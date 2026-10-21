package com.radustavila.nasaexplorer.feature.widget.aurora

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.designsystem.util.deepLinkIntent
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveAuroraAlertSettingsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveAuroraForecastUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveCurrentKpUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveKpForecastUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObserverLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.RequestSpaceWeatherSyncUseCase
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.SpaceWeatherDeepLink
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
 * Home screen widget with the current Kp, the coming peak and the aurora outlook at the saved place,
 * all from the space weather cache.
 *
 * It has no refresh job of its own: the space weather sync re-renders it after every successful Kp
 * refresh (see `SynchronizeSpaceWeatherUseCase`), and that sync only runs on a schedule while aurora
 * alerts are on, or when the space weather screen opens. So the widget always says how old its
 * reading is, and works offline from whatever was cached. With nothing cached it asks for one
 * refresh, like the APOD widget.
 */
class AuroraWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetSizes.all)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AuroraWidgetEntryPoint::class.java,
        )

        val alertSettings = entryPoint.observeAuroraAlertSettings().invoke().first()
        // The shared observer location first; aurora alerts kept their own place before it existed.
        val place = entryPoint.observeObserverLocation().invoke().first()?.coordinate
            ?: alertSettings.latitude?.let { latitude ->
                alertSettings.longitude?.let { longitude -> GeoCoordinate(latitude, longitude) }
            }
        val state = auroraWidgetState(
            current = entryPoint.observeCurrentKp().invoke().first(),
            forecast = entryPoint.observeKpForecast().invoke().first(),
            auroraMap = entryPoint.observeAuroraForecast().invoke().first(),
            place = place,
            now = entryPoint.timeProvider().now(),
            format = WidgetFormat(context.resources),
            resources = context.resources,
        )
        if (state == null) entryPoint.requestSpaceWeatherSync().invoke()

        val openIntent = context.deepLinkIntent(SpaceWeatherDeepLink.URI)
        val title = context.getString(R.string.aurora_widget_title)
        val emptyState = WidgetMessageState(context.getString(R.string.aurora_widget_empty), openIntent)

        provideContent {
            if (state != null) AuroraWidgetContent(state, title, openIntent) else WidgetMessage(emptyState)
        }
    }
}

class AuroraWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AuroraWidget()
}

/** What the widget needs from the dependency graph; widgets cannot use constructor injection. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AuroraWidgetEntryPoint {
    fun observeCurrentKp(): ObserveCurrentKpUseCase
    fun observeKpForecast(): ObserveKpForecastUseCase
    fun observeAuroraForecast(): ObserveAuroraForecastUseCase
    fun observeObserverLocation(): ObserveObserverLocationUseCase
    fun observeAuroraAlertSettings(): ObserveAuroraAlertSettingsUseCase
    fun requestSpaceWeatherSync(): RequestSpaceWeatherSyncUseCase
    fun timeProvider(): TimeProvider
}
