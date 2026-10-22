package com.radustavila.nasaexplorer.feature.dsn

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A side view of the antenna tilted to its elevation, with a dashed beam towards its target while a
 * signal is on, and a small compass showing its bearing. Large (70 m) dishes are drawn bigger.
 */
@Composable
internal fun DishDrawing(
    elevationDegrees: Double?,
    azimuthDegrees: Double?,
    isActive: Boolean,
    isLarge: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val ground = MaterialTheme.colorScheme.outlineVariant
    // An idle dish rests pointing straight up.
    val elevation = (elevationDegrees ?: REST_ELEVATION).coerceIn(0.0, REST_ELEVATION)
    val description = if (isActive && azimuthDegrees != null) {
        stringResource(R.string.dsn_dish_drawing, elevation.roundToInt(), azimuthDegrees.roundToInt())
    } else {
        stringResource(R.string.dsn_dish_drawing_idle)
    }
    Canvas(modifier.size(width = 88.dp, height = 56.dp).semantics { contentDescription = description }) {
        val stroke = 2.dp.toPx()
        val baseY = size.height - stroke
        // Left: the antenna seen from the side. Right: its bearing on a compass.
        val sideWidth = size.width - size.height * COMPASS_SHARE
        drawLine(ground, Offset(0f, baseY), Offset(sideWidth, baseY), strokeWidth = stroke)
        val pivot = Offset(sideWidth * 0.4f, baseY - size.height * 0.4f)
        drawPedestal(pivot, baseY, color, stroke)
        val radius = size.height * if (isLarge) 0.3f else 0.22f
        rotate(degrees = -elevation.toFloat(), pivot = pivot) {
            drawDish(pivot, radius, color, stroke)
            if (isActive) {
                drawLine(
                    color = color,
                    start = Offset(pivot.x + radius, pivot.y),
                    end = Offset(pivot.x + radius * BEAM_LENGTH, pivot.y),
                    strokeWidth = stroke / 2,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 2, stroke * 2)),
                )
            }
        }
        if (azimuthDegrees != null && isActive) {
            drawCompass(
                center = Offset(size.width - size.height * COMPASS_SHARE / 2, size.height / 2),
                radius = size.height * COMPASS_SHARE / 2 - stroke,
                azimuthDegrees = azimuthDegrees,
                color = color,
                ring = ground,
                stroke = stroke,
            )
        }
    }
}

private fun DrawScope.drawPedestal(pivot: Offset, baseY: Float, color: Color, stroke: Float) {
    val halfWidth = size.height * 0.12f
    val path = Path().apply {
        moveTo(pivot.x - halfWidth, baseY)
        lineTo(pivot.x, pivot.y)
        lineTo(pivot.x + halfWidth, baseY)
    }
    drawPath(path, color, style = Stroke(width = stroke))
}

/** A parabola opening to the right of [pivot], with its feed horn. */
private fun DrawScope.drawDish(pivot: Offset, radius: Float, color: Color, stroke: Float) {
    val rim = radius * 0.35f
    val dish = Path().apply {
        moveTo(pivot.x + rim, pivot.y - radius)
        quadraticTo(pivot.x - rim, pivot.y, pivot.x + rim, pivot.y + radius)
    }
    drawPath(dish, color, style = Stroke(width = stroke * 1.5f, cap = StrokeCap.Round))
    drawLine(color, pivot, Offset(pivot.x + radius, pivot.y), strokeWidth = stroke / 2)
}

/** North up; the needle points along the dish's bearing. */
@Suppress("LongParameterList") // Drawing parameters, all needed.
private fun DrawScope.drawCompass(
    center: Offset,
    radius: Float,
    azimuthDegrees: Double,
    color: Color,
    ring: Color,
    stroke: Float,
) {
    drawCircle(ring, radius, center, style = Stroke(width = stroke / 2))
    drawCircle(ring, stroke, Offset(center.x, center.y - radius))
    val angle = Math.toRadians(azimuthDegrees)
    val tip = Offset(center.x + (radius * sin(angle)).toFloat(), center.y - (radius * cos(angle)).toFloat())
    drawLine(color, center, tip, strokeWidth = stroke, cap = StrokeCap.Round)
}

private const val REST_ELEVATION = 90.0

/** How long the beam is drawn, in dish radii. */
private const val BEAM_LENGTH = 2.6f

/** The compass's width, as a share of the drawing's height. */
private const val COMPASS_SHARE = 0.5f
