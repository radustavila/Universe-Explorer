package com.radustavila.nasaexplorer.core.map.internal

import android.content.Context
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

/** One-time, process-wide MapLibre setup. No API key: every source is a keyless raster URL. */
internal object SpaceMapRuntime {
    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            MapLibre.getInstance(context.applicationContext)
            HttpRequestUtil.setOkHttpClient(
                OkHttpClient.Builder()
                    .addInterceptor(GibsTileInterceptor(onOutcome = TileLoadMonitor::report))
                    .build(),
            )
            initialized = true
        }
    }
}
