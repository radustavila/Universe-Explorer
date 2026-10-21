package com.radustavila.nasaexplorer.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's baseline profile (and its startup profile, used for dex layout):
 *
 * ```
 * ANDROID_SERIAL=emulator-5554 ./gradlew :app:generateBaselineProfile
 * ```
 *
 * The journey is a cold start to Today's cached feed, a scroll through it, and the other tabs'
 * first screens.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE_NAME, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        waitForToday()
        scrollToday()
        openTab("Universe")
        openTab("Earth")
        openTab("Favorites")
        openTab("Today")
    }
}
