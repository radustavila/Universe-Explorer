package com.radustavila.nasaexplorer.feature.widget

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import java.time.ZoneOffset
import java.util.Locale

internal val testContext: Context get() = ApplicationProvider.getApplicationContext()

internal val testResources: Resources get() = testContext.resources

/** Pinned zone and locale, so times read "14:20" and days "Mon 19 Oct" on any machine. */
internal fun testWidgetFormat() = WidgetFormat(testResources, zone = ZoneOffset.UTC, locale = Locale.UK)
