package com.radustavila.nasaexplorer.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette is deliberately dark-only: the app is a photo viewer, and astronomy imagery reads
 * best against a near-black ground.
 */
internal val DeepSpace = Color(0xFF07070D)
internal val DeepSpaceElevated = Color(0xFF101018)
internal val DeepSpaceCard = Color(0xFF16161F)

/**
 * Borders of text fields, segmented buttons and other controls: at least 3:1 against every
 * surface it sits on (WCAG 1.4.11), about 3.6:1 even on [DeepSpaceCard].
 */
internal val DeepSpaceOutline = Color(0xFF6E6E86)

/**
 * Chip borders and dividers (Material 3 draws chip outlines with `outlineVariant`): quieter than
 * [DeepSpaceOutline] but still at least 3:1 on [DeepSpaceCard].
 */
internal val DeepSpaceOutlineVariant = Color(0xFF64647D)
internal val Lavender = Color(0xFFCFC2FF)
internal val LavenderContainer = Color(0xFF2A2545)
internal val StardustCyan = Color(0xFF57D0E8)
internal val SolarAmber = Color(0xFFFFB74D)
internal val SolarAmberContainer = Color(0xFF3A2B10)
internal val MeteorRed = Color(0xFFE5484D)
internal val MoonGrey = Color(0xFFECECF3)
internal val MoonGreyDim = Color(0xFF9E9EB0)
