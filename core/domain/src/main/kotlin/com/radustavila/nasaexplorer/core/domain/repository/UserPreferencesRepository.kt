package com.radustavila.nasaexplorer.core.domain.repository

import com.radustavila.nasaexplorer.core.model.MeasurementSystem
import com.radustavila.nasaexplorer.core.model.TimeFormat
import com.radustavila.nasaexplorer.core.model.UnitPreferences
import kotlinx.coroutines.flow.Flow

/** Choices the user made in the app, as opposed to bookkeeping the app keeps for itself. */
interface UserPreferencesRepository {

    /** Whether a notification is posted when a new Astronomy Picture of the Day is published. */
    val newApodNotificationsEnabled: Flow<Boolean>

    suspend fun setNewApodNotificationsEnabled(enabled: Boolean)

    /** Whether each new picture of the day becomes the home screen wallpaper. */
    val dailyWallpaperEnabled: Flow<Boolean>

    suspend fun setDailyWallpaperEnabled(enabled: Boolean)

    /** Units for distances, speeds and clock times. */
    val unitPreferences: Flow<UnitPreferences>

    suspend fun setMeasurementSystem(system: MeasurementSystem)

    suspend fun setTimeFormat(format: TimeFormat)
}
