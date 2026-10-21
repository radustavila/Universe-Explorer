package com.radustavila.nasaexplorer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.theme.NasaTheme
import com.radustavila.nasaexplorer.core.designsystem.units.LocalUnitPreferences
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveUnitPreferencesUseCase
import com.radustavila.nasaexplorer.core.model.ApodWebLink
import com.radustavila.nasaexplorer.core.model.UnitPreferences
import com.radustavila.nasaexplorer.navigation.ApodWebLinks
import com.radustavila.nasaexplorer.ui.NasaApp
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var observeUnitPreferences: ObserveUnitPreferencesUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is dark whatever the system setting is, so the system bars are transparent with
        // light icons rather than following the platform's light/dark decision.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (!routeWebLink(isFirstCreation = savedInstanceState == null)) {
            finish()
            return
        }

        setContent {
            // Every screen formats distances, speeds and times through LocalUnitPreferences.
            val unitPreferences by remember { observeUnitPreferences() }
                .collectAsStateWithLifecycle(initialValue = UnitPreferences())
            CompositionLocalProvider(LocalUnitPreferences provides unitPreferences) {
                NasaTheme {
                    NasaApp()
                }
            }
        }
    }

    /**
     * An `apod.nasa.gov` link becomes the details deep link of its day, which navigation then
     * resolves like any other; a page the app does not show goes to the browser instead.
     *
     * @return false when the link was handed on and this activity has nothing to show.
     */
    private fun routeWebLink(isFirstCreation: Boolean): Boolean {
        if (!ApodWebLinks.isWebLink(intent)) return true
        val inAppUri = ApodWebLinks.inAppUriFor(intent, today = LocalDate.now(ApodWebLink.SITE_ZONE))
        if (inAppUri != null) {
            intent.data = inAppUri
            return true
        }
        // Recreated after process death: the browser already had its turn.
        val webUri = intent.data ?: return true
        return !(isFirstCreation && ApodWebLinks.openInBrowser(this, webUri))
    }
}
