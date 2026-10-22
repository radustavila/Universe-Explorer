package com.radustavila.nasaexplorer.core.map.internal

import android.graphics.RectF
import com.radustavila.nasaexplorer.core.map.CameraMove
import com.radustavila.nasaexplorer.core.map.MapCamera
import com.radustavila.nasaexplorer.core.map.SpaceMapState
import com.radustavila.nasaexplorer.core.map.gibs.GibsImagery
import com.radustavila.nasaexplorer.core.map.gibs.GibsLayer
import com.radustavila.nasaexplorer.core.map.overlay.GeoPoint
import com.radustavila.nasaexplorer.core.map.overlay.MapOverlay
import com.radustavila.nasaexplorer.core.map.overlay.MapShapeClick
import com.radustavila.nasaexplorer.core.map.TileLoadStatus
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet

/**
 * The only class that talks to MapLibre. [com.radustavila.nasaexplorer.core.map.SpaceMap] pushes
 * the latest imagery and overlays here on every composition; the controller remembers them until
 * the map and its style are ready, then applies only what changed.
 *
 * Layer stack, bottom to top: black background (space), Blue Marble base, the selected imagery
 * (its gap fill, then its layer), then each overlay's fill, outline, line and circle layers.
 *
 * New imagery (another day or layer) is added under the imagery on screen and only replaces it once
 * the map has gone idle with its tiles loaded, so switching never flashes the base. If none of its
 * tiles arrive, the old imagery stays and [tileStatus] says why.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SpaceMapController(
    private val mapView: MapView,
    private val state: SpaceMapState,
) {
    var onShapeClick: ((MapShapeClick) -> Unit)? = null
    var onMapClick: ((GeoPoint) -> Unit)? = null

    private val density = mapView.resources.displayMetrics.density
    private var map: MapLibreMap? = null
    private var style: Style? = null

    private var imagery: GibsImagery? = null
    private var appliedImagery: GibsImagery? = null
    private var imageryApplied = false

    /** The imagery on screen, and the imagery loading underneath it to replace it. */
    private var shown: ImageryStack? = null
    private var pending: ImageryStack? = null
    private var nextGeneration = 0
    private val settleTimeout = Runnable(::settlePending)

    private val baseTally = TileLoadMonitor.watch(listOf(GibsImagery(GibsLayer.BlueMarble).urlPrefixes.single()))
    private val statusTally = MutableStateFlow(baseTally)

    /** Tile health of the imagery last asked for (the base alone when there is none). */
    val tileStatus: Flow<TileLoadStatus> = statusTally.flatMapLatest { it.status }

    private var overlays: List<MapOverlay> = emptyList()
    private var appliedOverlays: List<MapOverlay> = emptyList()

    private var pendingMove: CameraMove? = null

    init {
        mapView.getMapAsync(::onMapReady)
        mapView.addOnDidBecomeIdleListener(::settlePending)
    }

    fun setImagery(imagery: GibsImagery?) {
        this.imagery = imagery
        applyImagery()
    }

    fun setOverlays(overlays: List<MapOverlay>) {
        require(overlays.map(MapOverlay::id).toSet().size == overlays.size) { "overlay ids must be unique" }
        this.overlays = overlays
        applyOverlays()
    }

    /** Stops counting tile requests for this map; called when its view is destroyed. */
    fun release() {
        mapView.removeCallbacks(settleTimeout)
        listOfNotNull(shown, pending).forEach { TileLoadMonitor.unwatch(it.tally) }
        TileLoadMonitor.unwatch(baseTally)
    }

    fun move(move: CameraMove) {
        val map = map ?: run { pendingMove = move; return }
        val update = CameraUpdateFactory.newCameraPosition(move.camera.toCameraPosition())
        if (move.animate) map.animateCamera(update, CAMERA_ANIMATION_MILLIS) else map.moveCamera(update)
    }

    private fun onMapReady(map: MapLibreMap) {
        this.map = map
        map.uiSettings.apply {
            isAttributionEnabled = false
            isLogoEnabled = false
            isCompassEnabled = false
            isRotateGesturesEnabled = false
            isTiltGesturesEnabled = false
        }
        map.setMinZoomPreference(MIN_ZOOM)
        map.setMaxZoomPreference(MAX_ZOOM)
        map.moveCamera(CameraUpdateFactory.newCameraPosition(state.camera.toCameraPosition()))
        pendingMove?.let { pendingMove = null; move(it) }

        map.addOnCameraIdleListener { state.camera = map.cameraPosition.toMapCamera() ?: state.camera }
        map.addOnMapClickListener { latLng -> handleClick(map, latLng) }

        map.setStyle(baseStyle()) { loaded ->
            style = loaded
            applyImagery()
            applyOverlays()
        }
    }

    private fun applyImagery() {
        val style = style ?: return
        if (imageryApplied && appliedImagery == imagery) return
        appliedImagery = imagery
        imageryApplied = true

        // Whatever was loading is no longer wanted.
        pending?.let { stale ->
            stale.removeFrom(style)
            pending = null
            mapView.removeCallbacks(settleTimeout)
        }
        val current = shown
        // Blue Marble is already the base; drawing it twice would only cost tiles.
        val target = imagery?.takeIf { it.layer != GibsLayer.BlueMarble }
        when {
            target == null -> {
                current?.removeFrom(style)
                shown = null
                statusTally.value = baseTally
            }
            target == current?.imagery -> statusTally.value = current.tally
            else -> {
                val stack = ImageryStack(target, nextGeneration++)
                stack.addTo(style, below = current?.bottomLayerId)
                pending = stack
                statusTally.value = stack.tally
                mapView.postDelayed(settleTimeout, SETTLE_TIMEOUT_MILLIS)
            }
        }
        state.isLoadingImagery = pending != null
    }

    /**
     * Once the map is idle, the pending imagery's tiles have arrived or failed: it replaces the
     * imagery on screen, or is dropped if nothing loaded. [tileStatus] keeps reporting it either way.
     */
    private fun settlePending() {
        val style = style ?: return
        val stack = pending ?: return
        pending = null
        mapView.removeCallbacks(settleTimeout)
        if (stack.tally.canReplaceShown) {
            shown?.removeFrom(style)
            shown = stack
        } else {
            stack.removeFrom(style)
        }
        state.isLoadingImagery = false
    }

    private fun applyOverlays() {
        val style = style ?: return
        if (appliedOverlays == overlays) return

        if (appliedOverlays.map(MapOverlay::id) == overlays.map(MapOverlay::id)) {
            // Same stack: replace data and visibility in place.
            overlays.zip(appliedOverlays).forEach { (overlay, applied) ->
                if (overlay.shapes != applied.shapes) {
                    style.getSourceAs<GeoJsonSource>(overlay.sourceId)?.setGeoJson(overlay.toFeatureCollection())
                }
                if (overlay.visible != applied.visible) {
                    val visibility = PropertyFactory.visibility(overlay.visibility)
                    overlay.layerIds.forEach { style.getLayer(it)?.setProperties(visibility) }
                }
            }
        } else {
            // Overlays added, removed or reordered: rebuild the stack so z-order matches the list.
            appliedOverlays.forEach { applied ->
                applied.layerIds.forEach(style::removeLayer)
                style.removeSource(applied.sourceId)
            }
            overlays.forEach { overlay ->
                style.addSource(GeoJsonSource(overlay.sourceId, overlay.toFeatureCollection()))
                overlay.styleLayers().forEach(style::addLayer)
            }
        }
        appliedOverlays = overlays
    }

    private fun handleClick(map: MapLibreMap, latLng: LatLng): Boolean {
        val position = GeoPoint(latLng.latitude.coerceIn(-90.0, 90.0), latLng.wrappedLongitude())
        val layerIds = appliedOverlays.filter(MapOverlay::visible).flatMap(MapOverlay::layerIds)
        val hit = if (layerIds.isEmpty()) {
            null
        } else {
            val point = map.projection.toScreenLocation(latLng)
            val slop = TAP_SLOP_DP * density
            map.queryRenderedFeatures(
                RectF(point.x - slop, point.y - slop, point.x + slop, point.y + slop),
                *layerIds.toTypedArray(),
            )
                .filter { it.hasProperty(OverlayProperties.SHAPE_ID) }
                .minByOrNull { GEOMETRY_PRIORITY.indexOf(it.geometry()?.type()).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
        }

        if (hit != null) {
            val click = MapShapeClick(
                overlayId = hit.getStringProperty(OverlayProperties.OVERLAY_ID),
                shapeId = hit.getStringProperty(OverlayProperties.SHAPE_ID),
                position = position,
            )
            onShapeClick?.let { it(click); return true }
        }
        onMapClick?.let { it(position); return true }
        return false
    }

    /**
     * One [GibsImagery] in the style: a raster source and layer per [GibsImagery.layers], with ids
     * unique to its [generation] so a new day can load while the previous one is still drawn.
     */
    private class ImageryStack(val imagery: GibsImagery, generation: Int) {
        private val ids = imagery.layers.indices.map { index -> "$IMAGERY_ID_PREFIX-$generation-$index" }
        val tally: TileTally = TileLoadMonitor.watch(imagery.urlPrefixes)

        val bottomLayerId: String get() = ids.first()

        /** Adds the layers bottom to top, under [below] or else straight above the base. */
        fun addTo(style: Style, below: String?) {
            var previous = BASE_LAYER_ID
            imagery.layers.zip(ids).forEach { (layer, id) ->
                style.addSource(rasterSource(id, layer, imagery.date))
                val raster = RasterLayer(id, id)
                if (below != null) style.addLayerBelow(raster, below) else style.addLayerAbove(raster, previous)
                previous = id
            }
        }

        fun removeFrom(style: Style) {
            ids.forEach { id ->
                style.removeLayer(id)
                style.removeSource(id)
            }
            TileLoadMonitor.unwatch(tally)
        }
    }

    private companion object {
        const val MIN_ZOOM = 0.5
        const val MAX_ZOOM = 11.0
        const val CAMERA_ANIMATION_MILLIS = 800
        const val TAP_SLOP_DP = 12f
        const val TILE_SIZE = 256
        const val TILEJSON_VERSION = "2.2.0"

        /** Gives up waiting for idle (e.g. while the view is paused) and settles on what arrived. */
        const val SETTLE_TIMEOUT_MILLIS = 20_000L

        const val BACKGROUND_LAYER_ID = "space-background"
        const val BASE_SOURCE_ID = "gibs-base"
        const val BASE_LAYER_ID = "gibs-base"
        const val IMAGERY_ID_PREFIX = "gibs-imagery"

        val GEOMETRY_PRIORITY = listOf("Point", "LineString", "Polygon")
        const val SPACE_COLOR = "#000000"

        fun baseStyle(): Style.Builder = Style.Builder()
            .withLayer(
                BackgroundLayer(BACKGROUND_LAYER_ID).withProperties(PropertyFactory.backgroundColor(SPACE_COLOR)),
            )
            .withSource(rasterSource(BASE_SOURCE_ID, GibsLayer.BlueMarble, date = null))
            .withLayer(RasterLayer(BASE_LAYER_ID, BASE_SOURCE_ID))

        fun rasterSource(id: String, layer: GibsLayer, date: LocalDate?): RasterSource {
            val tileSet = TileSet(TILEJSON_VERSION, layer.tileUrlTemplate(date)).apply {
                maxZoom = layer.maxZoom.toFloat()
                attribution = GIBS_ATTRIBUTION
            }
            return RasterSource(id, tileSet, TILE_SIZE)
        }

        const val GIBS_ATTRIBUTION = "NASA GIBS"
    }
}

/** What every tile URL of each layer starts with: the template up to its `{z}` placeholder. */
private val GibsImagery.urlPrefixes: List<String>
    get() = layers.map { it.tileUrlTemplate(date).substringBefore("{z}") }

private val MapOverlay.sourceId get() = "overlay-$id"
private val MapOverlay.fillLayerId get() = "overlay-$id-fill"
private val MapOverlay.outlineLayerId get() = "overlay-$id-outline"
private val MapOverlay.lineLayerId get() = "overlay-$id-line"
private val MapOverlay.circleLayerId get() = "overlay-$id-circle"
private val MapOverlay.layerIds get() = listOf(fillLayerId, outlineLayerId, lineLayerId, circleLayerId)
private val MapOverlay.visibility get() = if (visible) Property.VISIBLE else Property.NONE

private fun geometryIs(type: String) = Expression.eq(Expression.geometryType(), Expression.literal(type))
private fun colorOf(property: String) = Expression.toColor(Expression.get(property))
private fun numberOf(property: String) = Expression.toNumber(Expression.get(property))

private fun MapOverlay.styleLayers(): List<Layer> {
    val visibility = PropertyFactory.visibility(visibility)
    return listOf(
        FillLayer(fillLayerId, sourceId).withFilter(geometryIs("Polygon")).withProperties(
            visibility,
            PropertyFactory.fillColor(colorOf(OverlayProperties.COLOR)),
        ),
        LineLayer(outlineLayerId, sourceId).withFilter(geometryIs("Polygon")).withProperties(
            visibility,
            PropertyFactory.lineColor(colorOf(OverlayProperties.STROKE_COLOR)),
            PropertyFactory.lineWidth(numberOf(OverlayProperties.STROKE_WIDTH)),
        ),
        LineLayer(lineLayerId, sourceId).withFilter(geometryIs("LineString")).withProperties(
            visibility,
            PropertyFactory.lineColor(colorOf(OverlayProperties.COLOR)),
            PropertyFactory.lineWidth(numberOf(OverlayProperties.SIZE)),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
        CircleLayer(circleLayerId, sourceId).withFilter(geometryIs("Point")).withProperties(
            visibility,
            PropertyFactory.circleColor(colorOf(OverlayProperties.COLOR)),
            PropertyFactory.circleRadius(numberOf(OverlayProperties.SIZE)),
            PropertyFactory.circleStrokeColor(colorOf(OverlayProperties.STROKE_COLOR)),
            PropertyFactory.circleStrokeWidth(numberOf(OverlayProperties.STROKE_WIDTH)),
        ),
    )
}

private fun MapCamera.toCameraPosition(): CameraPosition = CameraPosition.Builder()
    .target(LatLng(center.latitude, center.longitude))
    .zoom(zoom)
    .build()

private fun CameraPosition.toMapCamera(): MapCamera? {
    val target = target ?: return null
    return MapCamera(GeoPoint(target.latitude.coerceIn(-90.0, 90.0), target.wrappedLongitude()), zoom)
}

/** MapLibre lets longitude run past ±180 after panning across the antimeridian. */
private fun LatLng.wrappedLongitude(): Double = wrap().longitude.coerceIn(-180.0, 180.0)
