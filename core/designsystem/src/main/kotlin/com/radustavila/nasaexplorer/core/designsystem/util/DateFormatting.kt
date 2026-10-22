package com.radustavila.nasaexplorer.core.designsystem.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val mediumDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** A date without a time. Dates with a time go through the user's time format: see `formatLocalDateTime`. */
fun LocalDate.formatMedium(locale: Locale = Locale.getDefault()): String =
    format(mediumDateFormatter.withLocale(locale))
