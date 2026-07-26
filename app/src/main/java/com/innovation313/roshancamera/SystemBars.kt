package com.innovation313.roshancamera

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Keeps controls clear of the status bar and the gesture bar.
 *
 * From Android 15 the system draws every app edge to edge once it targets API
 * 35 or higher, and `android:statusBarColor` / `android:navigationBarColor` are
 * ignored. Without this, the location pill sits under the clock and the shutter
 * button sits under the gesture handle — on the newest phones, which are
 * exactly the ones a reviewer will be holding.
 *
 * Padding rather than margin, so a view keeps its own background all the way to
 * the screen edge while its contents stay reachable.
 */
fun Activity.setUpSystemBars(lightIcons: Boolean = false) {
    WindowCompat.getInsetsController(window, window.decorView)
        .isAppearanceLightStatusBars = lightIcons
}

fun View.padForStatusBar() = applyBarInsets(top = true)

fun View.padForNavigationBar() = applyBarInsets(bottom = true)

private fun View.applyBarInsets(top: Boolean = false, bottom: Boolean = false) {
    val startTop = paddingTop
    val startBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val bars = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.updatePadding(
            top = if (top) startTop + bars.top else view.paddingTop,
            bottom = if (bottom) startBottom + bars.bottom else view.paddingBottom
        )
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}
