package com.radustavila.nasaexplorer.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

internal const val PACKAGE_NAME = "com.radustavila.nasaexplorer"

private const val CONTENT_TIMEOUT_MILLIS = 30_000L
private const val TAB_TIMEOUT_MILLIS = 10_000L

/** Waits for Today's list: the cached feed, or its empty/offline state when nothing is cached. */
internal fun MacrobenchmarkScope.waitForToday() {
    device.wait(Until.hasObject(By.scrollable(true)), CONTENT_TIMEOUT_MILLIS)
    device.waitForIdle()
}

/** Scrolls Today's feed down and back, so the list items' code is in the profile too. */
internal fun MacrobenchmarkScope.scrollToday() {
    val list = device.findObject(By.scrollable(true)) ?: return
    // Keeps the fling clear of the system gesture areas.
    list.setGestureMargin(device.displayWidth / 5)
    list.fling(Direction.DOWN)
    device.waitForIdle()
    list.fling(Direction.UP)
    device.waitForIdle()
}

/**
 * Opens a bottom bar tab by its label. The map screens are left out on purpose: the emulators
 * cannot run MapLibre's Vulkan renderer.
 */
internal fun MacrobenchmarkScope.openTab(label: String) {
    device.findObject(By.text(label))?.click() ?: return
    device.wait(Until.hasObject(By.text(label).selected(true)), TAB_TIMEOUT_MILLIS)
    device.waitForIdle()
}
