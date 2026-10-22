package com.radustavila.nasaexplorer.core.map.internal

import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.radustavila.nasaexplorer.core.map.SpaceMapState
import kotlinx.coroutines.flow.filterNotNull
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView

/** A [MapView] and the controller driving it, owned by one [SpaceMapState]. */
internal class SpaceMapHost(val mapView: MapView, val controller: SpaceMapController) {

    /**
     * The view, ready to be attached to a new parent. A list item that scrolled away may still hold
     * it; a view can only have one parent.
     */
    fun detachedMapView(): MapView = mapView.apply { (parent as? ViewGroup)?.removeView(this) }
}

/**
 * Creates the map view for [state] and ties it to the caller's lifetime: lifecycle callbacks, low
 * memory, camera moves and tile health. Where the view is drawn is up to [SpaceMap][com.radustavila.nasaexplorer.core.map.SpaceMap],
 * which may come and go (e.g. in a lazy list) without touching any of this.
 */
@Composable
internal fun SpaceMapHostEffects(state: SpaceMapState) {
    val context = LocalContext.current
    val host = remember(context, state) {
        SpaceMapRuntime.ensureInitialized(context)
        // TextureView so the map fades and slides with navigation transitions like any other view.
        val mapView = MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true))
        SpaceMapHost(mapView, SpaceMapController(mapView, state)).also { state.host = it }
    }

    MapViewLifecycle(host.mapView)

    DisposableEffect(context, host) {
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = host.mapView.onLowMemory()
        }
        context.registerComponentCallbacks(callbacks)
        onDispose {
            context.unregisterComponentCallbacks(callbacks)
            host.controller.release()
            if (state.host === host) state.host = null
        }
    }

    // Collected rather than read here, so a camera move does not recompose the whole screen.
    LaunchedEffect(host) {
        snapshotFlow { state.pendingMove }.filterNotNull().collect { move ->
            host.controller.move(move)
            state.pendingMove = null
        }
    }

    LaunchedEffect(host) {
        host.controller.tileStatus.collect { state.tileStatus = it }
    }
}

/** Forwards the host lifecycle to the [MapView], which needs every callback to render and clean up. */
@Composable
private fun MapViewLifecycle(mapView: MapView) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }
}
