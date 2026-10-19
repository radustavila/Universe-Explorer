package com.radustavila.nasaexplorer.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import com.radustavila.nasaexplorer.core.common.time.TimeProvider
import com.radustavila.nasaexplorer.core.domain.location.DeviceLocationProvider
import com.radustavila.nasaexplorer.core.domain.location.DeviceLocationResult
import com.radustavila.nasaexplorer.core.model.GeoCoordinate
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The platform's `LocationManager`, without Play services: a recent last-known fix when there is
 * one, otherwise a single fresh fix from the best provider coarse access allows.
 */
@Singleton
class AndroidDeviceLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
) : DeviceLocationProvider {

    private val locationManager: LocationManager? =
        ContextCompat.getSystemService(context, LocationManager::class.java)

    override fun hasPermission(): Boolean =
        LOCATION_PERMISSIONS.any { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

    @SuppressLint("MissingPermission") // Checked by hasPermission() first.
    override suspend fun currentLocation(): DeviceLocationResult {
        if (!hasPermission()) return DeviceLocationResult.PermissionDenied
        val manager = locationManager ?: return DeviceLocationResult.Unavailable
        if (!LocationManagerCompat.isLocationEnabled(manager)) return DeviceLocationResult.Unavailable

        val providers = usableProviders(manager)
        val location = try {
            recentLastKnown(manager, providers) ?: freshFix(manager, providers)
        } catch (_: SecurityException) {
            return DeviceLocationResult.PermissionDenied
        }
        return location?.let { DeviceLocationResult.Located(GeoCoordinate(it.latitude, it.longitude)) }
            ?: DeviceLocationResult.Unavailable
    }

    private fun usableProviders(manager: LocationManager): List<String> {
        val preferred = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        val available = manager.getProviders(true)
        return preferred.filter { it in available }
    }

    @SuppressLint("MissingPermission")
    private fun recentLastKnown(manager: LocationManager, providers: List<String>): Location? {
        val oldestAccepted = timeProvider.now().minus(MAX_LAST_KNOWN_AGE).toEpochMilli()
        return providers
            .mapNotNull { provider -> manager.getLastKnownLocation(provider) }
            .filter { it.time >= oldestAccepted }
            .maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private suspend fun freshFix(manager: LocationManager, providers: List<String>): Location? {
        val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER } ?: return null
        return withTimeoutOrNull(FIX_TIMEOUT.toMillis()) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(manager, provider, signal, DirectExecutor) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            }
        }
    }

    private object DirectExecutor : Executor {
        override fun execute(command: Runnable) = command.run()
    }

    private companion object {
        val LOCATION_PERMISSIONS = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        val MAX_LAST_KNOWN_AGE: Duration = Duration.ofMinutes(30)
        val FIX_TIMEOUT: Duration = Duration.ofSeconds(20)
    }
}
