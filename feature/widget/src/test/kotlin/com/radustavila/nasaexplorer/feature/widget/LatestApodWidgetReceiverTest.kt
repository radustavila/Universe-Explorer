package com.radustavila.nasaexplorer.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
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
class LatestApodWidgetReceiverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val receiver = ComponentName(context, LatestApodWidgetReceiver::class.java)

    @Test
    fun `the receiver is exported with its provider info`() {
        val info = context.packageManager.getReceiverInfo(receiver, PackageManager.GET_META_DATA)

        assertTrue(info.exported)
        assertEquals(
            R.xml.latest_apod_widget_info,
            info.metaData.getInt(AppWidgetManager.META_DATA_APPWIDGET_PROVIDER),
        )
    }

    @Test
    fun `the receiver handles widget updates`() {
        val update = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(context.packageName)

        val receivers = context.packageManager.queryBroadcastReceivers(update, 0)

        assertTrue(receivers.any { it.activityInfo.name == LatestApodWidgetReceiver::class.java.name })
    }
}
