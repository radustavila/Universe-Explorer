package com.radustavila.nasaexplorer.feature.widget.sky

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.designsystem.util.deepLinkIntent
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveIssPassesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObserverLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveSkyTonightUseCase
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.SkyTonightDeepLink
import com.radustavila.nasaexplorer.feature.widget.R
import com.radustavila.nasaexplorer.feature.widget.WidgetFormat
import com.radustavila.nasaexplorer.feature.widget.WidgetSizes
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/**
 * Home screen widget with tonight's Moon and the next visible ISS pass over the saved place.
 *
 * Entirely offline: the Moon is computed on the device, and passes are predicted from the cached
 * ISS orbit. It is re-rendered when new orbit data lands or the place changes (see
 * `RefreshOrbitalElementsUseCase` and the location use cases), and hourly by the provider info so
 * the phase and the next pass move along.
 */
class SkyTonightWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetSizes.all)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SkyTonightWidgetEntryPoint::class.java,
        )

        val now = entryPoint.timeProvider().now()
        val location = entryPoint.observeObserverLocation().invoke().first()
        // The phase is the same from everywhere, so without a place any point on Earth will do.
        val moon = entryPoint.observeSkyTonight().invoke(location?.coordinate ?: ANYWHERE).first().moon
        val state = skyTonightWidgetState(
            moon = moon,
            location = location,
            issForecast = location?.let { entryPoint.observeIssPasses().invoke(it.coordinate).first() },
            now = now,
            format = WidgetFormat(context.resources),
            resources = context.resources,
        )
        val moonPicture = drawMoon(
            phaseAngleDegrees = state.phaseAngleDegrees,
            sizePx = (MOON_SIZE_DP * context.resources.displayMetrics.density).roundToInt(),
            southernHemisphere = state.southernHemisphere,
        )
        val openIntent = context.deepLinkIntent(SkyTonightDeepLink.URI)
        val title = context.getString(R.string.sky_widget_title)

        provideContent {
            SkyTonightWidgetContent(state, title, moonPicture, openIntent)
        }
    }

    private companion object {
        val ANYWHERE = GeoCoordinate(latitude = 0.0, longitude = 0.0)

        /** The largest the Moon is drawn on the widget. */
        const val MOON_SIZE_DP = 56
    }
}

class SkyTonightWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SkyTonightWidget()
}

/** What the widget needs from the dependency graph; widgets cannot use constructor injection. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SkyTonightWidgetEntryPoint {
    fun observeObserverLocation(): ObserveObserverLocationUseCase
    fun observeIssPasses(): ObserveIssPassesUseCase
    fun observeSkyTonight(): ObserveSkyTonightUseCase
    fun timeProvider(): TimeProvider
}
