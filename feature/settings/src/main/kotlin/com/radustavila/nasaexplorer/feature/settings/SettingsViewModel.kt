package com.radustavila.nasaexplorer.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.nasaexplorer.core.domain.usecase.ClearCacheUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.MeasureStorageUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveAuroraAlertSettingsUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveDailyWallpaperEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveIssPassRemindersEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveNewApodNotificationsEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveObserverLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.ObserveUnitPreferencesUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetAuroraAlertEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetAuroraAlertLocationUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetDailyWallpaperEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetIssPassRemindersEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetMeasurementSystemUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetNewApodNotificationsEnabledUseCase
import com.radustavila.nasaexplorer.core.domain.usecase.SetTimeFormatUseCase
import com.radustavila.nasaexplorer.core.model.MeasurementSystem
import com.radustavila.nasaexplorer.core.model.TimeFormat
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Every user choice in one place. Each one is stored where it already lives (user preferences,
 * aurora alert settings, Sky preferences), so the screens that own a feature and this one always
 * agree; turning a notification on goes through the same use case as the feature's own switch.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeUnitPreferences: ObserveUnitPreferencesUseCase,
    observeNewApodNotifications: ObserveNewApodNotificationsEnabledUseCase,
    private val observeAuroraAlerts: ObserveAuroraAlertSettingsUseCase,
    observeIssReminders: ObserveIssPassRemindersEnabledUseCase,
    private val observeObserverLocation: ObserveObserverLocationUseCase,
    observeDailyWallpaper: ObserveDailyWallpaperEnabledUseCase,
    private val setMeasurementSystem: SetMeasurementSystemUseCase,
    private val setTimeFormat: SetTimeFormatUseCase,
    private val setNewApodNotifications: SetNewApodNotificationsEnabledUseCase,
    private val setAuroraAlertEnabled: SetAuroraAlertEnabledUseCase,
    private val setAuroraAlertLocation: SetAuroraAlertLocationUseCase,
    private val setIssReminders: SetIssPassRemindersEnabledUseCase,
    private val setDailyWallpaper: SetDailyWallpaperEnabledUseCase,
    private val measureStorage: MeasureStorageUseCase,
    private val clearCache: ClearCacheUseCase,
) : ViewModel() {

    private val storage = MutableStateFlow(StorageUiState())
    private val message = MutableStateFlow<SettingsMessage?>(null)

    private val choices = combine(
        observeUnitPreferences(),
        observeNewApodNotifications(),
        observeAuroraAlerts(),
        observeIssReminders(),
        observeDailyWallpaper(),
    ) { units, newApod, aurora, iss, wallpaper ->
        SettingsUiState(
            units = units,
            newApodNotificationsEnabled = newApod,
            auroraAlerts = aurora,
            issRemindersEnabled = iss,
            dailyWallpaperEnabled = wallpaper,
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        choices,
        observeObserverLocation(),
        storage,
        message,
    ) { choices, location, storage, message ->
        choices.copy(observerLocation = location, storage = storage, message = message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STATE_TIMEOUT_MILLIS), SettingsUiState())

    init {
        viewModelScope.launch { measure() }
    }

    fun onMeasurementSystemChange(system: MeasurementSystem) {
        viewModelScope.launch { setMeasurementSystem(system) }
    }

    fun onTimeFormatChange(format: TimeFormat) {
        viewModelScope.launch { setTimeFormat(format) }
    }

    fun onNewApodNotificationsChange(enabled: Boolean) {
        viewModelScope.launch { setNewApodNotifications(enabled) }
    }

    /**
     * Alerts need a place. Without one of their own they take the saved observer location; with
     * neither, the user is told to save one first.
     */
    fun onAuroraAlertsChange(enabled: Boolean) {
        viewModelScope.launch {
            val current = observeAuroraAlerts().first()
            if (enabled && !current.hasLocation) {
                val observer = observeObserverLocation().first()
                if (observer == null) {
                    message.value = SettingsMessage.AURORA_NEEDS_PLACE
                    return@launch
                }
                setAuroraAlertLocation(observer.coordinate.latitude, observer.coordinate.longitude)
            }
            setAuroraAlertEnabled(enabled)
        }
    }

    fun onUseSavedPlaceForAurora() {
        viewModelScope.launch {
            val observer = observeObserverLocation().first() ?: return@launch
            setAuroraAlertLocation(observer.coordinate.latitude, observer.coordinate.longitude)
        }
    }

    fun onIssRemindersChange(enabled: Boolean) {
        viewModelScope.launch { setIssReminders(enabled) }
    }

    fun onDailyWallpaperChange(enabled: Boolean) {
        viewModelScope.launch { setDailyWallpaper(enabled) }
    }

    fun onClearCache() {
        if (storage.value.isClearing) return
        storage.update { it.copy(isClearing = true) }
        viewModelScope.launch {
            val cleared = clearCache()
            measure()
            storage.update { it.copy(isClearing = false) }
            message.value = if (cleared) SettingsMessage.CACHE_CLEARED else SettingsMessage.CACHE_CLEAR_FAILED
        }
    }

    fun onMessageShown() {
        message.value = null
    }

    private suspend fun measure() {
        val entries = measureStorage()
        storage.update { it.copy(entries = entries) }
    }

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
