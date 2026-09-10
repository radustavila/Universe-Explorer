package com.radustavila.nasaexplorer.core.network.http

/**
 * Host-application-supplied HTTP settings. Keeps `:core:network` free of `BuildConfig` so it can be
 * reused by any module (and by tests) without an Android build type.
 */
data class NetworkConfiguration(
    val isLoggingEnabled: Boolean,
    val connectTimeoutSeconds: Long = 15,
    val readTimeoutSeconds: Long = 20,
    val callTimeoutSeconds: Long = 30,
)
