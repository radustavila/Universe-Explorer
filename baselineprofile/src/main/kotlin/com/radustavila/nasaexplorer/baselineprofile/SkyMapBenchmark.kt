package com.radustavila.nasaexplorer.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame times while dragging the sky map around, without and with the baseline profile:
 *
 * ```
 * ANDROID_SERIAL=emulator-5554 ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.radustavila.nasaexplorer.baselineprofile.SkyMapBenchmark
 * ```
 */
@RunWith(AndroidJUnit4::class)
class SkyMapBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun panWithoutCompilation() = pan(CompilationMode.None())

    @Test
    fun panWithBaselineProfile() = pan(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun pan(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = ITERATIONS,
        // No startup mode: one would kill the process between setup and measuring.
        setupBlock = {
            pressHome()
            killProcess()
            startActivityAndWait()
            waitForToday()
            openTab("Universe")
            openSkyMap()
        },
    ) {
        panSkyMap()
    }

    private companion object {
        const val ITERATIONS = 5
    }
}
