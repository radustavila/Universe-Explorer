package com.radustavila.nasaexplorer.core.designsystem.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val mediumDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private val dateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

fun LocalDate.formatMedium(locale: Locale = Locale.getDefault()): String =
    format(mediumDateFormatter.withLocale(locale))

fun Instant.formatDateTime(
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = dateTimeFormatter.withLocale(locale).format(atZone(zoneId))
