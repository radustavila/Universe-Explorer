package com.radustavila.nasaexplorer.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.test.core.app.ApplicationProvider
import com.radustavila.nasaexplorer.feature.widget.aurora.AuroraWidgetReceiver
import com.radustavila.nasaexplorer.feature.widget.earth.EarthTodayWidgetReceiver
import com.radustavila.nasaexplorer.feature.widget.launch.NextLaunchWidgetReceiver
import com.radustavila.nasaexplorer.feature.widget.sky.SkyTonightWidgetReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A widget only appears in the launcher's picker if its receiver is exported, listens for
 * APPWIDGET_UPDATE and points at its provider info; none of that is checked by the compiler.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetReceiversTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val receivers: Map<Class<out GlanceAppWidgetReceiver>, Int> = mapOf(
        LatestApodWidgetReceiver::class.java to R.xml.latest_apod_widget_info,
        AuroraWidgetReceiver::class.java to R.xml.aurora_widget_info,
        NextLaunchWidgetReceiver::class.java to R.xml.next_launch_widget_info,
        SkyTonightWidgetReceiver::class.java to R.xml.sky_widget_info,
        EarthTodayWidgetReceiver::class.java to R.xml.earth_widget_info,
    )

    @Test
    fun `every receiver is exported with its provider info`() {
        receivers.forEach { (receiver, providerInfo) ->
            val component = ComponentName(context, receiver)
            val info = context.packageManager.getReceiverInfo(component, PackageManager.GET_META_DATA)
            val metaData = info.metaData.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER)

            assertTrue(receiver.simpleName, info.exported)
            assertEquals(receiver.simpleName, providerInfo, metaData)
        }
    }

    @Test
    fun `every receiver handles widget updates`() {
        val update = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(context.packageName)

        val registered = context.packageManager.queryBroadcastReceivers(update, 0).map { it.activityInfo.name }

        receivers.keys.forEach { receiver -> assertTrue(receiver.simpleName, receiver.name in registered) }
    }
}
