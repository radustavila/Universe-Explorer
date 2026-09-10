package com.radustavila.nasaexplorer.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = NebulaBlue,
    secondary = StardustCyan,
    tertiary = SolarAmber,
    error = MeteorRed,
)

private val DarkColors = darkColorScheme(
    primary = StardustCyan,
    onPrimary = DeepSpace,
    secondary = NebulaBlue,
    tertiary = SolarAmber,
    error = MeteorRed,
    background = DeepSpace,
    surface = NebulaBlueDark,
    onSurface = MoonGrey,
)

@Composable
fun NasaTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        useDynamicColor && supportsDynamicColor -> {
            val context = LocalContext.current
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        useDarkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NasaTypography,
        content = content,
    )
}
