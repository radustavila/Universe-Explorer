package com.radustavila.nasaexplorer.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The single colour scheme of the application.
 *
 * Material You is intentionally not used: the wallpaper-derived palette tinted the photography and
 * varied per device, which made the one thing the app exists to show look different everywhere.
 */
internal val SpaceColors = darkColorScheme(
    primary = Lavender,
    onPrimary = DeepSpace,
    primaryContainer = LavenderContainer,
    onPrimaryContainer = Lavender,
    secondary = StardustCyan,
    onSecondary = DeepSpace,
    secondaryContainer = LavenderContainer,
    onSecondaryContainer = Lavender,
    tertiary = SolarAmber,
    onTertiary = DeepSpace,
    tertiaryContainer = SolarAmberContainer,
    onTertiaryContainer = SolarAmber,
    error = MeteorRed,
    onError = DeepSpace,
    background = DeepSpace,
    onBackground = MoonGrey,
    surface = DeepSpace,
    onSurface = MoonGrey,
    surfaceVariant = DeepSpaceVariant,
    onSurfaceVariant = MoonGreyDim,
    surfaceContainer = DeepSpaceElevated,
    surfaceContainerHigh = DeepSpaceCard,
    surfaceContainerHighest = DeepSpaceCard,
    surfaceContainerLow = DeepSpaceElevated,
    surfaceContainerLowest = DeepSpace,
    outline = DeepSpaceOutline,
    outlineVariant = DeepSpaceOutlineVariant,
)

@Composable
fun NasaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SpaceColors,
        typography = NasaTypography,
        content = content,
    )
}
