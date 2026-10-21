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
import com.radustavila.nasaexplorer.core.model.UnitPreferences
import com.radustavila.nasaexplorer.ui.NasaApp
import dagger.hilt.android.AndroidEntryPoint
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
}
