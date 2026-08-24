package io.github.mouse233.localsendkotlin.ui

import android.app.Activity
import android.os.Build
import android.view.View
import io.github.mouse233.localsendkotlin.R

/** Keeps system bars visually continuous with the app chrome on gesture and button navigation. */
object SystemBars {
    @Suppress("DEPRECATION")
    fun apply(activity: Activity) {
        val window = activity.window
        window.statusBarColor = activity.resources.getColor(R.color.brand_primary)
        window.navigationBarColor = activity.resources.getColor(R.color.window_background)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = activity.resources.getColor(R.color.window_background)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        var flags = window.decorView.systemUiVisibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags
    }
}
