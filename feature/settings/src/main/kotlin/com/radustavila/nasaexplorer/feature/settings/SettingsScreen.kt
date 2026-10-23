package com.radustavila.nasaexplorer.feature.settings

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.notification.rememberNotificationPermissionToggle
import com.radustavila.nasaexplorer.core.designsystem.units.LocalUnitPreferences
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.domain.storage.StorageKind
import com.radustavila.nasaexplorer.core.domain.usecase.CheckEarthquakeAlertUseCase
import com.radustavila.nasaexplorer.core.model.AuroraAlertSettings
import com.radustavila.nasaexplorer.core.model.GeomagneticStorm
import com.radustavila.nasaexplorer.core.model.MeasurementSystem
import com.radustavila.nasaexplorer.core.model.StargazingAlertSettings
import com.radustavila.nasaexplorer.core.model.StargazingRating
import com.radustavila.nasaexplorer.core.model.TimeFormat
import java.time.LocalDate
import java.time.ZoneId

const val SETTINGS_LIST_TEST_TAG = "settings-list"
const val SETTINGS_NEW_APOD_SWITCH_TEST_TAG = "settings-new-apod"
const val SETTINGS_AURORA_SWITCH_TEST_TAG = "settings-aurora"
const val SETTINGS_ISS_SWITCH_TEST_TAG = "settings-iss"
const val SETTINGS_STARGAZING_SWITCH_TEST_TAG = "settings-stargazing"
const val SETTINGS_EARTHQUAKE_SWITCH_TEST_TAG = "settings-earthquake"
const val SETTINGS_WALLPAPER_SWITCH_TEST_TAG = "settings-wallpaper"
const val SETTINGS_UNITS_EXAMPLE_TEST_TAG = "settings-units-example"
const val SETTINGS_CLEAR_CACHE_TEST_TAG = "settings-clear-cache"
const val SETTINGS_CLEAR_CACHE_CONFIRM_TEST_TAG = "settings-clear-cache-confirm"
const val SETTINGS_CUSTOMIZE_TODAY_TEST_TAG = "settings-customize-today"

fun measurementOptionTestTag(system: MeasurementSystem) = "settings-measurement-${system.name}"

fun timeFormatOptionTestTag(format: TimeFormat) = "settings-time-format-${format.name}"

fun stargazingMinimumOptionTestTag(score: Int) = "settings-stargazing-minimum-$score"

@Composable
internal fun SettingsScreen(
    onBackClick: () -> Unit,
    onCustomizeTodayClick: () -> Unit,
    locationSection: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    SettingsMessageEffect(uiState.message, snackbarHostState, viewModel::onMessageShown)
    LifecycleResumeEffect(viewModel) {
        viewModel.onScreenResumed()
        onPauseOrDispose { }
    }

    SettingsContent(
        uiState = uiState,
        notifications = rememberNotificationSwitches(uiState, viewModel, snackbarHostState),
        onBackClick = onBackClick,
        onCustomizeTodayClick = onCustomizeTodayClick,
        onMeasurementSystemChange = viewModel::onMeasurementSystemChange,
        onTimeFormatChange = viewModel::onTimeFormatChange,
        onUseSavedPlaceForAurora = viewModel::onUseSavedPlaceForAurora,
        onDailyWallpaperChange = viewModel::onDailyWallpaperChange,
        onClearCache = viewModel::onClearCache,
        locationSection = locationSection,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * Every notification switch, reading "on" only while the system lets the app notify, like the
 * features' own.
 */
@Composable
private fun rememberNotificationSwitches(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState,
): NotificationSwitches {
    val blocked = stringResource(R.string.settings_notifications_blocked)

    val newApod = rememberNotificationPermissionToggle(
        isEnabled = uiState.newApodNotificationsEnabled,
        onEnabledChange = viewModel::onNewApodNotificationsChange,
        snackbarHostState = snackbarHostState,
        blockedMessage = blocked,
    )
    val aurora = rememberNotificationPermissionToggle(
        isEnabled = uiState.auroraAlerts.enabled,
        onEnabledChange = viewModel::onAuroraAlertsChange,
        snackbarHostState = snackbarHostState,
        blockedMessage = blocked,
    )
    val iss = rememberNotificationPermissionToggle(
        isEnabled = uiState.issRemindersEnabled,
        onEnabledChange = viewModel::onIssRemindersChange,
        snackbarHostState = snackbarHostState,
        blockedMessage = blocked,
    )
    val stargazing = rememberNotificationPermissionToggle(
        isEnabled = uiState.stargazingAlerts.enabled,
        onEnabledChange = viewModel::onStargazingAlertsChange,
        snackbarHostState = snackbarHostState,
        blockedMessage = blocked,
    )
    val earthquakes = rememberNotificationPermissionToggle(
        isEnabled = uiState.earthquakeAlertsEnabled,
        onEnabledChange = viewModel::onEarthquakeAlertsChange,
        snackbarHostState = snackbarHostState,
        blockedMessage = blocked,
    )

    return NotificationSwitches(
        newApodOn = newApod.isOn,
        onNewApodClick = newApod.onClick,
        auroraOn = aurora.isOn,
        onAuroraClick = aurora.onClick,
        issOn = iss.isOn,
        onIssClick = iss.onClick,
        stargazingOn = stargazing.isOn,
        onStargazingClick = stargazing.onClick,
        onStargazingMinimumScoreChange = viewModel::onStargazingMinimumScoreChange,
        earthquakeOn = earthquakes.isOn,
        onEarthquakeClick = earthquakes.onClick,
    )
}

/** What the notification switches show (permission-aware) and what tapping them does. */
internal data class NotificationSwitches(
    val newApodOn: Boolean,
    val onNewApodClick: () -> Unit,
    val auroraOn: Boolean,
    val onAuroraClick: () -> Unit,
    val issOn: Boolean,
    val onIssClick: () -> Unit,
    val stargazingOn: Boolean = false,
    val onStargazingClick: () -> Unit = {},
    val onStargazingMinimumScoreChange: (Int) -> Unit = {},
    val earthquakeOn: Boolean = false,
    val onEarthquakeClick: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    uiState: SettingsUiState,
    notifications: NotificationSwitches,
    onBackClick: () -> Unit,
    onCustomizeTodayClick: () -> Unit,
    onMeasurementSystemChange: (MeasurementSystem) -> Unit,
    onTimeFormatChange: (TimeFormat) -> Unit,
    onUseSavedPlaceForAurora: () -> Unit,
    onDailyWallpaperChange: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    locationSection: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = CONTENT_MAX_WIDTH)
                    .fillMaxSize()
                    .testTag(SETTINGS_LIST_TEST_TAG),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item(key = "today-title") { SectionTitle(R.string.settings_today_title) }
                item(key = "customize-today") {
                    NavigationRow(
                        title = stringResource(R.string.settings_customize_today),
                        description = stringResource(R.string.settings_customize_today_description),
                        onClick = onCustomizeTodayClick,
                        testTag = SETTINGS_CUSTOMIZE_TODAY_TEST_TAG,
                    )
                }
                unitsSection(uiState, onMeasurementSystemChange, onTimeFormatChange)
                notificationsSection(uiState, notifications, onUseSavedPlaceForAurora)
                item(key = "location-title") { SectionTitle(R.string.settings_location_title) }
                item(key = "location") {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Description(R.string.settings_location_description)
                        locationSection(Modifier.fillMaxWidth())
                    }
                }
                item(key = "wallpaper-title") { SectionTitle(R.string.settings_wallpaper_title) }
                item(key = "wallpaper") {
                    SwitchRow(
                        title = stringResource(R.string.settings_wallpaper_title),
                        description = stringResource(R.string.settings_wallpaper_description),
                        checked = uiState.dailyWallpaperEnabled,
                        onClick = { onDailyWallpaperChange(!uiState.dailyWallpaperEnabled) },
                        testTag = SETTINGS_WALLPAPER_SWITCH_TEST_TAG,
                    )
                }
                storageSection(uiState.storage, onClearClick = { confirmClear = true })
            }
        }
    }

    // Outside the list: a dialog opened from a lazy item keeps Compose busy under Robolectric.
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_clear_cache_dialog_title)) },
            text = { Text(stringResource(R.string.settings_clear_cache_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClearCache()
                    },
                    modifier = Modifier.testTag(SETTINGS_CLEAR_CACHE_CONFIRM_TEST_TAG),
                ) { Text(stringResource(R.string.settings_clear_cache_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.settings_clear_cache_cancel)) }
            },
        )
    }
}

private fun LazyListScope.unitsSection(
    uiState: SettingsUiState,
    onMeasurementSystemChange: (MeasurementSystem) -> Unit,
    onTimeFormatChange: (TimeFormat) -> Unit,
) {
    item(key = "units-title") { SectionTitle(R.string.settings_units_title) }
    item(key = "measurement") {
        val automatic = MeasurementSystem.AUTOMATIC.resolve(LocalLocale.current.platformLocale)
        ChoiceGroup(
            title = stringResource(R.string.settings_measurement_title),
            options = MeasurementSystem.entries,
            selected = uiState.units.measurementSystem,
            label = { system ->
                when (system) {
                    MeasurementSystem.AUTOMATIC -> stringResource(
                        R.string.settings_measurement_automatic,
                        stringResource(
                            if (automatic == MeasurementSystem.IMPERIAL) {
                                R.string.settings_measurement_imperial_short
                            } else {
                                R.string.settings_measurement_metric_short
                            },
                        ),
                    )

                    MeasurementSystem.METRIC -> stringResource(R.string.settings_measurement_metric)
                    MeasurementSystem.IMPERIAL -> stringResource(R.string.settings_measurement_imperial)
                }
            },
            testTag = ::measurementOptionTestTag,
            onSelect = onMeasurementSystemChange,
        )
    }
    item(key = "time-format") {
        val systemIs24Hour = DateFormat.is24HourFormat(LocalContext.current)
        ChoiceGroup(
            title = stringResource(R.string.settings_time_format_title),
            options = TimeFormat.entries,
            selected = uiState.units.timeFormat,
            label = { format ->
                when (format) {
                    TimeFormat.SYSTEM -> stringResource(
                        R.string.settings_time_format_system,
                        stringResource(if (systemIs24Hour) R.string.settings_time_format_24 else R.string.settings_time_format_12),
                    )

                    TimeFormat.TWELVE_HOUR -> stringResource(R.string.settings_time_format_12)
                    TimeFormat.TWENTY_FOUR_HOUR -> stringResource(R.string.settings_time_format_24)
                }
            },
            testTag = ::timeFormatOptionTestTag,
            onSelect = onTimeFormatChange,
        )
    }
    item(key = "units-example") {
        // The state's own choice, so the example follows a tap at once.
        CompositionLocalProvider(LocalUnitPreferences provides uiState.units) {
            val units = rememberUnitFormatter()
            val evening = remember { EXAMPLE_DAY.atTime(EXAMPLE_HOUR, EXAMPLE_MINUTE).atZone(ZoneId.systemDefault()).toInstant() }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = stringResource(
                        R.string.settings_units_example,
                        units.distance(EXAMPLE_ALTITUDE_KM),
                        units.speedPerHour(EXAMPLE_SPEED_KM_PER_HOUR),
                        units.time(evening),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(SETTINGS_UNITS_EXAMPLE_TEST_TAG),
                )
                Description(R.string.settings_units_note)
            }
        }
    }
}

private fun LazyListScope.notificationsSection(
    uiState: SettingsUiState,
    notifications: NotificationSwitches,
    onUseSavedPlaceForAurora: () -> Unit,
) {
    item(key = "notifications-title") { SectionTitle(R.string.settings_notifications_title) }
    item(key = "new-apod") {
        SwitchRow(
            title = stringResource(R.string.settings_new_apod_title),
            description = stringResource(R.string.settings_new_apod_description),
            checked = notifications.newApodOn,
            onClick = notifications.onNewApodClick,
            testTag = SETTINGS_NEW_APOD_SWITCH_TEST_TAG,
        )
    }
    item(key = "aurora") {
        Column {
            SwitchRow(
                title = stringResource(R.string.settings_aurora_title),
                description = auroraSummary(uiState.auroraAlerts),
                checked = notifications.auroraOn,
                onClick = notifications.onAuroraClick,
                testTag = SETTINGS_AURORA_SWITCH_TEST_TAG,
            )
            if (uiState.canUseSavedPlaceForAurora) {
                TextButton(onClick = onUseSavedPlaceForAurora, modifier = Modifier.padding(start = 8.dp)) {
                    Text(stringResource(R.string.settings_aurora_use_saved_place))
                }
            }
        }
    }
    item(key = "iss") {
        SwitchRow(
            title = stringResource(R.string.settings_iss_title),
            description = stringResource(
                if (uiState.observerLocation == null) R.string.settings_iss_needs_place else R.string.settings_iss_description,
            ),
            checked = notifications.issOn,
            onClick = notifications.onIssClick,
            testTag = SETTINGS_ISS_SWITCH_TEST_TAG,
        )
    }
    stargazingAlertItem(uiState, notifications)
    item(key = "earthquake") {
        val units = rememberUnitFormatter()
        SwitchRow(
            title = stringResource(R.string.settings_earthquake_title),
            description = if (uiState.observerLocation == null) {
                stringResource(R.string.settings_earthquake_needs_place)
            } else {
                stringResource(
                    R.string.settings_earthquake_description,
                    units.distance(CheckEarthquakeAlertUseCase.NEARBY_RADIUS_KM),
                    units.distance(CheckEarthquakeAlertUseCase.TSUNAMI_RADIUS_KM),
                )
            },
            checked = notifications.earthquakeOn,
            onClick = notifications.onEarthquakeClick,
            testTag = SETTINGS_EARTHQUAKE_SWITCH_TEST_TAG,
        )
    }
}

private fun LazyListScope.stargazingAlertItem(uiState: SettingsUiState, notifications: NotificationSwitches) {
    item(key = "stargazing") {
        val minimum = uiState.stargazingAlerts.minimumScore
        Column {
            SwitchRow(
                title = stringResource(R.string.settings_stargazing_title),
                description = if (uiState.observerLocation == null) {
                    stringResource(R.string.settings_stargazing_needs_place)
                } else {
                    stringResource(R.string.settings_stargazing_description, minimum)
                },
                checked = notifications.stargazingOn,
                onClick = notifications.onStargazingClick,
                testTag = SETTINGS_STARGAZING_SWITCH_TEST_TAG,
            )
            if (notifications.stargazingOn) {
                ChoiceGroup(
                    title = stringResource(R.string.settings_stargazing_minimum_title),
                    options = StargazingAlertSettings.MINIMUM_SCORE_CHOICES,
                    selected = minimum,
                    label = { score -> stringResource(stargazingMinimumLabel(score), score) },
                    testTag = ::stargazingMinimumOptionTestTag,
                    onSelect = notifications.onStargazingMinimumScoreChange,
                )
            }
        }
    }
}

@StringRes
private fun stargazingMinimumLabel(score: Int): Int = when {
    score >= StargazingRating.EXCELLENT_FROM -> R.string.settings_stargazing_minimum_excellent
    score > StargazingRating.GOOD_FROM -> R.string.settings_stargazing_minimum_very_good
    else -> R.string.settings_stargazing_minimum_good
}

private fun LazyListScope.storageSection(storage: StorageUiState, onClearClick: () -> Unit) {
    item(key = "storage-title") { SectionTitle(R.string.settings_storage_title) }
    val entries = storage.entries
    if (entries == null) {
        item(key = "storage-measuring") { Description(R.string.settings_storage_measuring, Modifier.padding(horizontal = 16.dp)) }
    } else {
        entries.forEach { entry ->
            item(key = "storage-${entry.kind}") { SizeRow(stringResource(entry.kind.labelRes), entry.bytes) }
        }
        storage.totalBytes?.let { total ->
            item(key = "storage-total") {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                SizeRow(stringResource(R.string.settings_storage_total), total, emphasized = true)
            }
        }
    }
    item(key = "storage-clear") {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onClearClick,
                enabled = !storage.isClearing,
                modifier = Modifier.testTag(SETTINGS_CLEAR_CACHE_TEST_TAG),
            ) { Text(stringResource(R.string.settings_clear_cache)) }
            if (storage.isClearing) {
                Spacer(Modifier.width(12.dp))
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun auroraSummary(settings: AuroraAlertSettings): String {
    val latitude = settings.latitude
    val longitude = settings.longitude
    if (latitude == null || longitude == null) return stringResource(R.string.settings_aurora_no_place)
    val place = rememberUnitFormatter().coordinate(latitude, longitude)
    val kp = settings.minimumKp ?: return stringResource(R.string.settings_aurora_unreachable, place)
    if (kp == 0) return stringResource(R.string.settings_aurora_any_kp, place)
    val threshold = GeomagneticStorm.forLevel(kp)
        ?.let { stringResource(R.string.settings_aurora_kp_storm, kp, it.name) }
        ?: stringResource(R.string.settings_aurora_kp, kp)
    return stringResource(R.string.settings_aurora_threshold, place, threshold)
}

@get:StringRes
private val StorageKind.labelRes: Int
    get() = when (this) {
        StorageKind.IMAGE_CACHE -> R.string.settings_storage_image_cache
        StorageKind.HTTP_CACHE -> R.string.settings_storage_http_cache
        StorageKind.CACHED_DATA -> R.string.settings_storage_cached_data
        StorageKind.LIBRARY -> R.string.settings_storage_library
        StorageKind.OFFLINE_FAVORITES -> R.string.settings_storage_offline_favorites
        StorageKind.PODCAST_DOWNLOADS -> R.string.settings_storage_podcast_downloads
        StorageKind.MOON_TILES -> R.string.settings_storage_moon_tiles
    }

@Composable
private fun SectionTitle(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun Description(@StringRes text: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun <T> ChoiceGroup(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    testTag: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = option == selected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag(testTag(option)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Text(label(option), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onClick: () -> Unit, testTag: String) {
    // The whole row toggles, not only the switch.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onClick() })
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun NavigationRow(title: String, description: String, onClick: () -> Unit, testTag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun SizeRow(label: String, bytes: Long, emphasized: Boolean = false) {
    val style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = style, modifier = Modifier.weight(1f))
        val context = LocalContext.current
        // ICU number and unit formatting: not redone on every recomposition while scrolling.
        val size = remember(context, bytes) { Formatter.formatShortFileSize(context, bytes) }
        Text(size, style = style)
    }
}

@Composable
private fun SettingsMessageEffect(message: SettingsMessage?, snackbarHostState: SnackbarHostState, onShown: () -> Unit) {
    val text = message?.let {
        stringResource(
            when (it) {
                SettingsMessage.CACHE_CLEARED -> R.string.settings_cache_cleared
                SettingsMessage.CACHE_CLEAR_FAILED -> R.string.settings_cache_clear_failed
                SettingsMessage.AURORA_NEEDS_PLACE -> R.string.settings_aurora_needs_place
                SettingsMessage.STARGAZING_NEEDS_PLACE -> R.string.settings_stargazing_needs_place_message
                SettingsMessage.EARTHQUAKE_NEEDS_PLACE -> R.string.settings_earthquake_needs_place_message
            },
        )
    }
    LaunchedEffect(message) {
        if (text == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        onShown()
    }
}

private val CONTENT_MAX_WIDTH = 640.dp

/** The ISS: a familiar altitude, speed and an evening time for the units example. */
private const val EXAMPLE_ALTITUDE_KM = 420.0
private const val EXAMPLE_SPEED_KM_PER_HOUR = 27_600.0
private val EXAMPLE_DAY: LocalDate = LocalDate.of(2026, 1, 1)
private const val EXAMPLE_HOUR = 21
private const val EXAMPLE_MINUTE = 30
