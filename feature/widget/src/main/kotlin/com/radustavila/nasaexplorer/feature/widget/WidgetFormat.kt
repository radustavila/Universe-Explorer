package com.radustavila.nasaexplorer.feature.widget

import android.content.res.Resources
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Times and numbers as the widgets show them, in the device's zone and locale (passed in so tests
 * can pin them).
 *
 * A widget is a snapshot that is re-rendered only now and then, so it prefers clock times ("14:30")
 * to relative ones that would go stale on the home screen; the few relative texts have a coarse
 * resolution for the same reason.
 */
internal class WidgetFormat(
    private val resources: Resources,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault(),
) {
    private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    private val dayFormatter = DateTimeFormatter.ofPattern("EEE d MMM", locale)

    fun time(instant: Instant): String = timeFormatter.format(instant.atZone(zone))

    fun day(instant: Instant): String = dayFormatter.format(instant.atZone(zone))

    /** Just the time on [now]'s day, the day and time otherwise: "21:43", "Tue 21 Oct 21:43". */
    fun dayAndTime(instant: Instant, now: Instant): String =
        if (instant.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()) {
            time(instant)
        } else {
            "${day(instant)} ${time(instant)}"
        }

    fun decimal(value: Double, fractionDigits: Int): String = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
    }.format(value)

    fun percent(value: Int): String = resources.getString(R.string.widget_percent, value)

    /** "in 12 m", "in 3 h 20 m", "in 2 d 5 h": minutes under a day, hours beyond. */
    fun timeUntil(target: Instant, now: Instant): String {
        val minutes = Duration.between(now, target).toMinutes().coerceAtLeast(0)
        val days = minutes / MINUTES_PER_DAY
        val hours = minutes % MINUTES_PER_DAY / MINUTES_PER_HOUR
        return when {
            days > 0 -> resources.getString(R.string.widget_in_days_hours, days, hours)
            hours > 0 -> resources.getString(R.string.widget_in_hours_minutes, hours, minutes % MINUTES_PER_HOUR)
            else -> resources.getString(R.string.widget_in_minutes, minutes)
        }
    }

    /** "45 m", "5 h", "2 d": how old cached data is. */
    fun age(since: Instant, now: Instant): String {
        val minutes = Duration.between(since, now).toMinutes().coerceAtLeast(0)
        return when {
            minutes >= MINUTES_PER_DAY -> resources.getString(R.string.widget_age_days, minutes / MINUTES_PER_DAY)
            minutes >= MINUTES_PER_HOUR -> resources.getString(R.string.widget_age_hours, minutes / MINUTES_PER_HOUR)
            else -> resources.getString(R.string.widget_age_minutes, minutes)
        }
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60L
        const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
    }
}
