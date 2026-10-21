package com.radustavila.nasaexplorer.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until

internal const val PACKAGE_NAME = "com.radustavila.nasaexplorer"

private const val CONTENT_TIMEOUT_MILLIS = 30_000L
private const val TAB_TIMEOUT_MILLIS = 10_000L
private const val STALE_ATTEMPTS = 3

// A fifth of the screen width on each side keeps flings clear of the system gesture areas.
private const val GESTURE_MARGIN_DIVISOR = 5

// FEED_LIST_TEST_TAG and FEED_APOD_CARD_TEST_TAG in :feature:feed; NasaApp exposes test tags as
// resource ids.
private const val FEED_LIST = "feed-list"
private const val FEED_APOD_CARD = "feed-apod-card"

/** Waits for Today's list: the cached feed, or its empty/offline state when nothing is cached. */
internal fun MacrobenchmarkScope.waitForToday() {
    device.wait(Until.hasObject(By.scrollable(true)), CONTENT_TIMEOUT_MILLIS)
    device.waitForIdle()
}

/** Scrolls Today's feed down and back, so the list items' code is in the profile too. */
internal fun MacrobenchmarkScope.scrollToday() {
    for (direction in listOf(Direction.DOWN, Direction.UP)) {
        retryingStale {
            val list = device.findObject(By.res(FEED_LIST)) ?: return
            list.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
            list.fling(direction)
        }
        device.waitForIdle()
    }
}

/** Opens the first APOD entry's details from Today's feed, then goes back to the feed. */
internal fun MacrobenchmarkScope.openDetails() {
    retryingStale {
        val list = device.findObject(By.res(FEED_LIST)) ?: return
        list.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
        // Today's header cards sit above the entries and can fill the first screen.
        val card = list.scrollUntil(Direction.DOWN, Until.findObject(By.res(FEED_APOD_CARD))) ?: return
        card.click()
    }
    device.wait(Until.gone(By.res(FEED_LIST)), TAB_TIMEOUT_MILLIS)
    device.waitForIdle()
    device.pressBack()
    device.wait(Until.hasObject(By.res(FEED_LIST)), TAB_TIMEOUT_MILLIS)
    device.waitForIdle()
}

/**
 * Opens a bottom bar tab by its label. The map screens are left out on purpose: the emulators
 * cannot run MapLibre's Vulkan renderer.
 */
internal fun MacrobenchmarkScope.openTab(label: String) {
    retryingStale { device.findObject(By.text(label))?.click() ?: return }
    device.wait(Until.hasObject(By.text(label).selected(true)), TAB_TIMEOUT_MILLIS)
    device.waitForIdle()
}

/**
 * Runs [action] again when the node it found is replaced before it acts on it: Compose recomposes
 * Today while its header cards and the sync fill in, which a loaded emulator makes likely.
 */
@Suppress("SwallowedException")
private inline fun retryingStale(action: () -> Unit) {
    repeat(STALE_ATTEMPTS - 1) {
        try {
            return action()
        } catch (_: StaleObjectException) {
            // Found again on the next attempt.
        }
    }
    action()
}
