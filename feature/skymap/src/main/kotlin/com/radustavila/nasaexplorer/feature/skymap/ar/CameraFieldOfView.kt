package com.radustavila.nasaexplorer.feature.skymap.ar

import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * A back camera's lens and sensor as Camera2 reports them: [focalLengthMillimetres] and the size of
 * the sensor's active area, in the sensor's own (landscape) orientation.
 */
internal data class CameraLens(
    val focalLengthMillimetres: Double,
    val activeWidthMillimetres: Double,
    val activeHeightMillimetres: Double,
) {
    companion object {
        /**
         * From Camera2's `LENS_INFO_AVAILABLE_FOCAL_LENGTHS` (the first), `SENSOR_INFO_PHYSICAL_SIZE`
         * and the pixel and active array sizes: the physical size covers the whole pixel array, of
         * which only the active area makes pictures.
         */
        fun fromSensor(
            focalLengthMillimetres: Double,
            physicalWidthMillimetres: Double,
            physicalHeightMillimetres: Double,
            pixelArrayWidth: Int,
            pixelArrayHeight: Int,
            activeWidth: Int,
            activeHeight: Int,
        ): CameraLens = CameraLens(
            focalLengthMillimetres = focalLengthMillimetres,
            activeWidthMillimetres = physicalWidthMillimetres * activeWidth / pixelArrayWidth,
            activeHeightMillimetres = physicalHeightMillimetres * activeHeight / pixelArrayHeight,
        )
    }
}

/** Angles a picture spans across and up, degrees. */
internal data class FieldOfView(val horizontalDegrees: Double, val verticalDegrees: Double)

/** How wide the preview on the screen sees, from the lens and how the picture is cropped. */
internal object CameraFieldOfView {

    private const val QUARTER_TURN = 90
    private const val HALF_TURN = 180

    /**
     * The angles a [viewWidth] by [viewHeight] preview shows at [zoomRatio], when it fills the view
     * and crops the overflow (CameraX's `FILL_CENTER`).
     *
     * The camera crops its sensor to the stream's aspect ([streamWidth] by [streamHeight], in the
     * sensor's orientation), the largest such rectangle in the middle of the active area; the
     * picture is turned by [rotationDegrees] to stand upright on the screen, then scaled to cover
     * the view, which cuts off its longer overflow. A zoom ratio crops the middle of what is left.
     */
    @Suppress("LongParameterList")
    fun preview(
        lens: CameraLens,
        streamWidth: Int,
        streamHeight: Int,
        rotationDegrees: Int,
        viewWidth: Int,
        viewHeight: Int,
        zoomRatio: Double = 1.0,
    ): FieldOfView {
        // The part of the sensor the stream covers, in millimetres, in the sensor's orientation.
        val streamAspect = streamWidth.toDouble() / streamHeight
        val activeAspect = lens.activeWidthMillimetres / lens.activeHeightMillimetres
        val sensorWidth = min(lens.activeWidthMillimetres, lens.activeHeightMillimetres * streamAspect)
        val sensorHeight = if (streamAspect > activeAspect) sensorWidth / streamAspect else lens.activeHeightMillimetres

        // The same, turned upright on the screen.
        val isSideways = rotationDegrees.mod(HALF_TURN) == QUARTER_TURN
        val shownWidth = (if (isSideways) streamHeight else streamWidth).toDouble()
        val shownHeight = (if (isSideways) streamWidth else streamHeight).toDouble()
        val cropWidth = if (isSideways) sensorHeight else sensorWidth
        val cropHeight = if (isSideways) sensorWidth else sensorHeight

        val fill = max(viewWidth / shownWidth, viewHeight / shownHeight)
        val visibleWidth = cropWidth * (viewWidth / fill / shownWidth) / zoomRatio
        val visibleHeight = cropHeight * (viewHeight / fill / shownHeight) / zoomRatio
        return FieldOfView(
            horizontalDegrees = angle(visibleWidth, lens.focalLengthMillimetres),
            verticalDegrees = angle(visibleHeight, lens.focalLengthMillimetres),
        )
    }

    /** The angle across the shorter side of a [viewWidth] by [viewHeight] view: what the sky map needs. */
    fun FieldOfView.shorterSide(viewWidth: Int, viewHeight: Int): Double =
        if (viewWidth <= viewHeight) horizontalDegrees else verticalDegrees

    /** [degrees] seen at zoom 1, seen through [zoomRatio]: digital zoom crops the middle. */
    fun zoomed(degrees: Double, zoomRatio: Double): Double =
        Math.toDegrees(2.0 * atan(tan(Math.toRadians(degrees) / 2.0) / zoomRatio))

    private fun angle(sizeMillimetres: Double, focalLengthMillimetres: Double): Double =
        Math.toDegrees(2.0 * atan(sizeMillimetres / 2.0 / focalLengthMillimetres))
}
