package com.radustavila.nasaexplorer.feature.settings.today

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.SatelliteAlt
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.radustavila.nasaexplorer.core.model.TodayCardId
import com.radustavila.nasaexplorer.feature.settings.R

fun customizeTodayRowTestTag(card: TodayCardId) = "customize-today-row-${card.name}"

fun customizeTodaySwitchTestTag(card: TodayCardId) = "customize-today-switch-${card.name}"

fun customizeTodayHandleTestTag(card: TodayCardId) = "customize-today-handle-${card.name}"

/* How Customize Today names and pictures each card; every TodayCardId needs all three. */

@get:StringRes
internal val TodayCardId.titleRes: Int
    get() = when (this) {
        TodayCardId.EARTH_TODAY -> R.string.customize_today_card_earth_today
        TodayCardId.EARTH_EVENTS -> R.string.customize_today_card_earth_events
        TodayCardId.NEXT_LAUNCHES -> R.string.customize_today_card_launches
        TodayCardId.ISS_NOW -> R.string.customize_today_card_iss
        TodayCardId.SKY_TONIGHT -> R.string.customize_today_card_sky
        TodayCardId.SPACE_WEATHER -> R.string.customize_today_card_space_weather
        TodayCardId.DSN_NOW -> R.string.customize_today_card_dsn
        TodayCardId.NASA_NEWS -> R.string.customize_today_card_news
    }

@get:StringRes
internal val TodayCardId.descriptionRes: Int
    get() = when (this) {
        TodayCardId.EARTH_TODAY -> R.string.customize_today_card_earth_today_description
        TodayCardId.EARTH_EVENTS -> R.string.customize_today_card_earth_events_description
        TodayCardId.NEXT_LAUNCHES -> R.string.customize_today_card_launches_description
        TodayCardId.ISS_NOW -> R.string.customize_today_card_iss_description
        TodayCardId.SKY_TONIGHT -> R.string.customize_today_card_sky_description
        TodayCardId.SPACE_WEATHER -> R.string.customize_today_card_space_weather_description
        TodayCardId.DSN_NOW -> R.string.customize_today_card_dsn_description
        TodayCardId.NASA_NEWS -> R.string.customize_today_card_news_description
    }

internal val TodayCardId.icon: ImageVector
    get() = when (this) {
        TodayCardId.EARTH_TODAY -> Icons.Outlined.Public
        TodayCardId.EARTH_EVENTS -> Icons.Outlined.LocalFireDepartment
        TodayCardId.NEXT_LAUNCHES -> Icons.Outlined.RocketLaunch
        TodayCardId.ISS_NOW -> Icons.Outlined.SatelliteAlt
        TodayCardId.SKY_TONIGHT -> Icons.Outlined.NightsStay
        TodayCardId.SPACE_WEATHER -> Icons.Outlined.WbSunny
        TodayCardId.DSN_NOW -> Icons.Outlined.SettingsInputAntenna
        TodayCardId.NASA_NEWS -> Icons.Outlined.Newspaper
    }
