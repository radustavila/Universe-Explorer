package com.radustavila.nasaexplorer.core.designsystem.motion

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.lang.ref.WeakReference

/**
 * Whether the user asked the system for less motion: Developer options or Accessibility "Remove
 * animations", which sets the animator duration scale to 0.
 *
 * Compose's own animations already honour that scale. Use this for motion the app starts by
 * itself, outside Compose's animation clock: a pager that advances on its own, a map camera flight,
 * an image crossfade. With reduced motion they should start paused, jump, or not run at all.
 *
 * Updates while the app runs when the setting changes.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val state = remember(context.applicationContext) { ReducedMotionSetting.observe(context) }
    return state.value
}

/**
 * One observer for the whole process, however many composables ask: the setting changes rarely and
 * an image list would otherwise register an observer per cell.
 */
private object ReducedMotionSetting {
    // Weak, so this object never pins a Context; tests get a new application per test case.
    private var application = WeakReference<Context>(null)
    private var state: MutableState<Boolean>? = null

    fun observe(context: Context): MutableState<Boolean> {
        val app = context.applicationContext ?: context
        state?.takeIf { application.get() === app }?.let { return it }

        val fresh = mutableStateOf(app.isAnimatorDisabled())
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                fresh.value = app.isAnimatorDisabled()
            }
        }
        app.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        application = WeakReference(app)
        state = fresh
        return fresh
    }

    private fun Context.isAnimatorDisabled(): Boolean =
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
