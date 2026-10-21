package com.radustavila.nasaexplorer.feature.settings

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.annotation.StringRes
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.nasaexplorer.core.designsystem.notification.rememberNotificationPermissionToggle
import com.radustavila.nasaexplorer.core.designsystem.units.LocalUnitPreferences
import com.radustavila.nasaexplorer.core.designsystem.units.rememberUnitFormatter
import com.radustavila.nasaexplorer.core.domain.storage.StorageKind
import com.radustavila.nasaexplorer.core.model.AuroraAlertSettings
import com.radustavila.nasaexplorer.core.model.GeomagneticStorm
import com.radustavila.nasaexplorer.core.model.MeasurementSystem
import com.radustavila.nasaexplorer.core.model.TimeFormat
import java.time.LocalDate
import java.time.ZoneId

const val SETTINGS_LIST_TEST_TAG = "settings-list"
const val SETTINGS_NEW_APOD_SWITCH_TEST_TAG = "settings-new-apod"
const val SETTINGS_AURORA_SWITCH_TEST_TAG = "settings-aurora"
const val SETTINGS_ISS_SWITCH_TEST_TAG = "settings-iss"
const val SETTINGS_WALLPAPER_SWITCH_TEST_TAG = "settings-wallpaper"
const val SETTINGS_UNITS_EXAMPLE_TEST_TAG = "settings-units-example"
const val SETTINGS_CLEAR_CACHE_TEST_TAG = "settings-clear-cache"
const val SETTINGS_CLEAR_CACHE_CONFIRM_TEST_TAG = "settings-clear-cache-confirm"

fun measurementOptionTestTag(system: MeasurementSystem) = "settings-measurement-${system.name}"

fun timeFormatOptionTestTag(format: TimeFormat) = "settings-time-format-${format.name}"

@Composable
internal fun SettingsScreen(
    onBackClick: () -> Unit,
    locationSection: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val blocked = stringResource(R.string.settings_notifications_blocked)

    // Every switch reads "on" only while the system lets the app notify, like the features' own.
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

    SettingsMessageEffect(uiState.message, snackbarHostState, viewModel::onMessageShown)

    SettingsContent(
        uiState = uiState,
        notifications = NotificationSwitches(
            newApodOn = newApod.isOn,
            onNewApodClick = newApod.onClick,
            auroraOn = aurora.isOn,
            onAuroraClick = aurora.onClick,
            issOn = iss.isOn,
            onIssClick = iss.onClick,
        ),
        onBackClick = onBackClick,
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

/** What the notification switches show (permission-aware) and what tapping them does. */
internal data class NotificationSwitches(
    val newApodOn: Boolean,
    val onNewApodClick: () -> Unit,
    val auroraOn: Boolean,
    val onAuroraClick: () -> Unit,
    val issOn: Boolean,
    val onIssClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
    uiState: SettingsUiState,
    notifications: NotificationSwitches,
    onBackClick: () -> Unit,
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
private fun SizeRow(label: String, bytes: Long, emphasized: Boolean = false) {
    val style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = style, modifier = Modifier.weight(1f))
        Text(Formatter.formatShortFileSize(LocalContext.current, bytes), style = style)
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
