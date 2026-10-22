package com.radustavila.nasaexplorer.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

private const val COMPASS_SECTOR = 45.0

private val COMPASS_POINTS = intArrayOf(
    R.string.iss_pass_compass_n,
    R.string.iss_pass_compass_ne,
    R.string.iss_pass_compass_e,
    R.string.iss_pass_compass_se,
    R.string.iss_pass_compass_s,
    R.string.iss_pass_compass_sw,
    R.string.iss_pass_compass_w,
    R.string.iss_pass_compass_nw,
)
private const val FULL_TURN = 360.0

/** "north-east" and the like, for where a pass starts: the string resource of the nearest of 8 points. */
internal fun compassPoint(azimuth: Double): Int {
    val normalized = (azimuth % FULL_TURN + FULL_TURN) % FULL_TURN
    val index = ((normalized + COMPASS_SECTOR / 2) / COMPASS_SECTOR).toInt() % COMPASS_POINTS.size
    return COMPASS_POINTS[index]
}

/** Opens the app where the user left it; null on a device without a launcher entry. */
internal fun Context.openAppIntent(requestCode: Int): PendingIntent? =
    packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        PendingIntent.getActivity(this, requestCode, intent, flags)
    }
