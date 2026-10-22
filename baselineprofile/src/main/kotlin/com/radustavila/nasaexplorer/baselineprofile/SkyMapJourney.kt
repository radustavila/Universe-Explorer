package com.radustavila.nasaexplorer.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

private const val SKY_MAP_TIMEOUT_MILLIS = 30_000L
private const val DIALOG_TIMEOUT_MILLIS = 10_000L
private const val PAN_SPEED_PIXELS_PER_SECOND = 1_500
private const val PAN_FRACTION = 0.7f

private const val CHOOSE_A_CITY = "Choose a city"

// SKY_MAP_CANVAS_TEST_TAG in :feature:skymap.
private const val SKY_MAP_CANVAS = "sky-map-canvas"

/**
 * From the Universe tab's hub to the sky map, saving Bucharest as the place on a fresh install
 * (through Sky tonight's city picker the map shows when no place is saved).
 */
internal fun MacrobenchmarkScope.openSkyMap() {
    val hub = device.findObject(By.scrollable(true)) ?: return
    hub.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
    hub.scrollUntil(Direction.DOWN, Until.findObject(By.text("Sky map")))?.click() ?: return
    waitForMapOrPicker()
    chooseCityIfAsked()
    device.wait(Until.hasObject(By.res(SKY_MAP_CANVAS)), SKY_MAP_TIMEOUT_MILLIS)
    device.waitForIdle()
}

/** Drags the sky left and right; the first drag leaves compass mode for touch mode. */
internal fun MacrobenchmarkScope.panSkyMap() {
    val canvas = device.findObject(By.res(SKY_MAP_CANVAS)) ?: return
    canvas.setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
    repeat(2) {
        canvas.swipe(Direction.LEFT, PAN_FRACTION, PAN_SPEED_PIXELS_PER_SECOND)
        canvas.swipe(Direction.RIGHT, PAN_FRACTION, PAN_SPEED_PIXELS_PER_SECOND)
        canvas.swipe(Direction.UP, PAN_FRACTION, PAN_SPEED_PIXELS_PER_SECOND)
        canvas.swipe(Direction.DOWN, PAN_FRACTION, PAN_SPEED_PIXELS_PER_SECOND)
    }
    device.waitForIdle()
}

/** Either the map, or the location picker when no place is saved yet. */
private fun MacrobenchmarkScope.waitForMapOrPicker() {
    val deadline = System.currentTimeMillis() + SKY_MAP_TIMEOUT_MILLIS
    while (
        !device.hasObject(By.res(SKY_MAP_CANVAS)) &&
        !device.hasObject(By.text(CHOOSE_A_CITY)) &&
        System.currentTimeMillis() < deadline
    ) {
        device.waitForIdle()
    }
}

private fun MacrobenchmarkScope.chooseCityIfAsked() {
    val choose = device.findObject(By.text(CHOOSE_A_CITY)) ?: return
    choose.click()
    val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), DIALOG_TIMEOUT_MILLIS) ?: return
    field.text = "Bucharest"
    device.wait(Until.findObject(By.text("Romania")), DIALOG_TIMEOUT_MILLIS)?.click()
}
