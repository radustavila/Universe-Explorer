package com.radustavila.nasaexplorer.core.network.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

/**
 * Verifies that the monitor reports connectivity from callbacks rather than polling, and that a
 * connected-but-unvalidated network counts as offline.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidNetworkMonitorTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkMonitor = AndroidNetworkMonitor(
        context = context,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun `a validated network is reported as online`() = runTest {
        setActiveNetworkCapabilities(isValidated = true)

        networkMonitor.isOnline.test {
            assertTrue(awaitItem())
        }
    }

    @Test
    fun `a connected but unvalidated network is reported as offline`() = runTest {
        setActiveNetworkCapabilities(isValidated = false)

        networkMonitor.isOnline.test {
            assertFalse(awaitItem())
        }
    }

    @Test
    fun `an unmetered network allows bulk downloads, a metered one does not`() = runTest {
        setActiveNetworkCapabilities(isValidated = true, isUnmetered = true)
        assertTrue(networkMonitor.isUnmetered())

        setActiveNetworkCapabilities(isValidated = true, isUnmetered = false)
        assertFalse(networkMonitor.isUnmetered())
    }

    private fun setActiveNetworkCapabilities(isValidated: Boolean, isUnmetered: Boolean = false) {
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (isUnmetered) shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        if (isValidated) {
            shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            shadowOf(capabilities).removeCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        shadowOf(connectivityManager).setNetworkCapabilities(
            connectivityManager.activeNetwork,
            capabilities,
        )
    }
}
