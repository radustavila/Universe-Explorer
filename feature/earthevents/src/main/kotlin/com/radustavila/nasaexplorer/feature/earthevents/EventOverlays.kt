package com.radustavila.nasaexplorer.feature.earthevents

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.radustavila.nasaexplorer.core.map.overlay.GeoPoint
import com.radustavila.nasaexplorer.core.map.overlay.MapMarker
import com.radustavila.nasaexplorer.core.map.overlay.MapOverlay
import com.radustavila.nasaexplorer.core.map.overlay.MapPolygon
import com.radustavila.nasaexplorer.core.map.overlay.MapPolyline
import com.radustavila.nasaexplorer.core.map.overlay.MapShape
import com.radustavila.nasaexplorer.core.model.EarthEvent
import com.radustavila.nasaexplorer.core.model.EarthEventCategory
import com.radustavila.nasaexplorer.core.model.EarthEventShape
import com.radustavila.nasaexplorer.core.model.EventRecency
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import com.radustavila.nasaexplorer.core.model.recency
import java.time.LocalDate
import kotlin.math.abs

/**
 * The map overlays for [events]: per category, its areas, then its tracks, then its markers, so
 * every marker sits above every line and area. One overlay per category and kind keeps the stack
 * stable while the filter only flips [MapOverlay.visible]. The [selectedEventId]'s marker is drawn
 * again, larger, in a last overlay on top.
 *
 * Shapes fade with their event's [EventRecency] (as of [today]): full colour for current events,
 * muted for older ones, faint for history. Older events are drawn first, so newer ones sit on top.
 *
 * Every shape id starts with its event's id (see [eventIdOf]), so any tap opens that event.
 */
internal fun eventOverlays(
    events: List<EarthEvent>,
    hiddenCategories: Set<EarthEventCategory>,
    selectedEventId: String?,
    today: LocalDate,
): List<MapOverlay> =
    baseEventOverlays(events, hiddenCategories, today) + selectionOverlay(events, hiddenCategories, selectedEventId)

/** Everything [eventOverlays] draws except the selection, which changes far more often. */
internal fun baseEventOverlays(
    events: List<EarthEvent>,
    hiddenCategories: Set<EarthEventCategory>,
    today: LocalDate,
): List<MapOverlay> {
    val byCategory = events.sortedBy(EarthEvent::lastUpdate).groupBy(EarthEvent::category)
    val categories = EarthEventCategory.entries.filter(byCategory::containsKey)

    fun layer(kind: String, shapes: (List<EarthEvent>) -> List<MapShape>) = categories.map { category ->
        MapOverlay(
            id = "$kind-${category.key}",
            shapes = shapes(byCategory.getValue(category)),
            visible = category !in hiddenCategories,
        )
    }

    return layer(KIND_AREAS) { it.mapNotNull { event -> event.area(event.recency(today)) } } +
        layer(KIND_TRACKS) { it.flatMap { event -> event.trackLines(event.recency(today)) } } +
        layer(KIND_MARKERS) { it.map { event -> event.marker(selected = false, event.recency(today)) } }
}

/** The selected event's marker again, larger; empty without a selection or when its category is hidden. */
internal fun selectionOverlay(
    events: List<EarthEvent>,
    hiddenCategories: Set<EarthEventCategory>,
    selectedEventId: String?,
): MapOverlay {
    val selected = events.firstOrNull { it.id == selectedEventId }?.takeIf { it.category !in hiddenCategories }
    return MapOverlay(
        id = SELECTED_OVERLAY_ID,
        shapes = listOfNotNull(selected?.marker(selected = true, EventRecency.Current)),
    )
}

/** The event a tapped shape belongs to. */
internal fun eventIdOf(shapeId: String): String = shapeId.substringBefore(SHAPE_ID_SEPARATOR)

/** Where the event is now: its latest point, or the middle of its latest area. */
internal val EarthEvent.position: GeoPoint get() = latest.shape.center.toGeoPoint()

private fun EarthEvent.marker(selected: Boolean, recency: EventRecency) = MapMarker(
    id = id,
    position = position,
    color = category.color.copy(alpha = recency.emphasis),
    radius = if (selected) 10.dp else recency.markerRadius,
    strokeColor = if (selected) Color.White else Color.Black.copy(alpha = MARKER_STROKE_ALPHA * recency.emphasis),
    strokeWidth = if (selected) 3.dp else 1.dp,
)

/** How strongly an event is drawn: older reports fade so current ones stand out. */
private val EventRecency.emphasis: Float
    get() = when (this) {
        EventRecency.Current -> 1f
        EventRecency.Latest -> LATEST_EMPHASIS
        EventRecency.History, EventRecency.Expired -> HISTORY_EMPHASIS
    }

private val EventRecency.markerRadius: Dp
    get() = when (this) {
        EventRecency.Current -> 6.dp
        EventRecency.Latest -> 5.dp
        EventRecency.History, EventRecency.Expired -> 4.dp
    }

/**
 * The reported positions joined oldest to newest. A track that crosses the antimeridian is split
 * there; drawn as one line it would cross the whole map the other way.
 */
private fun EarthEvent.trackLines(recency: EventRecency): List<MapPolyline> {
    val points = track.map(GeoCoordinate::toGeoPoint).dedupeConsecutive()
    if (points.size < 2) return emptyList()

    val segments = mutableListOf(mutableListOf(points.first()))
    points.zipWithNext().forEach { (previous, next) ->
        if (abs(next.longitude - previous.longitude) > HALF_WORLD_DEGREES) segments += mutableListOf<GeoPoint>()
        segments.last() += next
    }
    return segments.filter { it.size >= 2 }.mapIndexed { index, segment ->
        MapPolyline(
            id = "$id${SHAPE_ID_SEPARATOR}track$index",
            points = segment,
            color = category.color.copy(alpha = TRACK_ALPHA * recency.emphasis),
            width = 2.5.dp,
        )
    }
}

private fun EarthEvent.area(recency: EventRecency): MapPolygon? {
    val polygon = latest.shape as? EarthEventShape.Polygon ?: return null
    val rings = polygon.rings.map { ring -> ring.map(GeoCoordinate::toGeoPoint).dedupeConsecutive() }
    val outline = rings.first().takeIf { it.distinct().size >= 3 } ?: return null
    return MapPolygon(
        id = "$id${SHAPE_ID_SEPARATOR}area",
        outline = outline,
        holes = rings.drop(1).filter { it.distinct().size >= 3 },
        fillColor = category.color.copy(alpha = AREA_FILL_ALPHA * recency.emphasis),
        strokeColor = category.color.copy(alpha = recency.emphasis),
        strokeWidth = 1.5.dp,
    )
}

private fun GeoCoordinate.toGeoPoint() = GeoPoint(latitude = latitude, longitude = longitude)

private fun List<GeoPoint>.dedupeConsecutive(): List<GeoPoint> =
    filterIndexed { index, point -> index == 0 || point != this[index - 1] }

private const val KIND_AREAS = "event-areas"
private const val KIND_TRACKS = "event-tracks"
private const val KIND_MARKERS = "event-markers"
internal const val SELECTED_OVERLAY_ID = "event-selected"
private const val SHAPE_ID_SEPARATOR = '#'

private const val HALF_WORLD_DEGREES = 180.0
private const val MARKER_STROKE_ALPHA = 0.6f
private const val TRACK_ALPHA = 0.85f
private const val AREA_FILL_ALPHA = 0.3f
private const val LATEST_EMPHASIS = 0.65f
private const val HISTORY_EMPHASIS = 0.35f
