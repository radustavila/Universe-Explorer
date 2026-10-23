package com.radustavila.nasaexplorer.feature.quiz

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.radustavila.nasaexplorer.core.model.QuizTopic
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The score as a line to paste anywhere: "NASA Explorer daily quiz, 23 Oct: 4/5" and one square
 * per answer, green when right. No question is given away.
 */
object QuizShareText {

    private const val RIGHT_SQUARE = "🟩"
    private const val WRONG_SQUARE = "🟥"

    fun daily(
        resources: Resources,
        appName: String,
        date: LocalDate,
        results: List<Boolean>,
        locale: Locale = Locale.getDefault(),
    ): String {
        val pattern = DateFormat.getBestDateTimePattern(locale, "dMMM")
        val day = date.format(DateTimeFormatter.ofPattern(pattern, locale))
        val squares = results.joinToString("") { if (it) RIGHT_SQUARE else WRONG_SQUARE }
        return resources.getString(R.string.quiz_share_daily, appName, day, results.count { it }, results.size, squares)
    }

    fun practice(resources: Resources, appName: String, topic: QuizTopic, score: Int, total: Int): String =
        resources.getString(R.string.quiz_share_practice, appName, resources.getString(topic.label()), score, total)
}

/** Opens the system share sheet with [text]. */
@Composable
internal fun rememberTextSharer(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) { { text -> context.shareText(text) } }
}

internal val Context.appName: String get() = applicationInfo.loadLabel(packageManager).toString()

private fun Context.shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        val chooser = Intent.createChooser(send, getString(R.string.quiz_share_chooser))
        startActivity(chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Nothing can share text; there is nothing better to offer.
    }
}
