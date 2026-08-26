package io.github.mouse233.localsendkotlin.ui

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View

/** Keeps system bars visually continuous with the app chrome on gesture and button navigation. */
object SystemBars {
    @Suppress("DEPRECATION")
    fun apply(activity: Activity) {
        val window = activity.window
        val primary = ThemeColors.primaryColor(activity)
        val dark = ThemeColors.isDark(activity)
        val background = ThemeColors.backgroundColor(activity)
        window.statusBarColor = primary
        window.navigationBarColor = background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = background
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        var flags = window.decorView.systemUiVisibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = if (ThemeColors.foregroundColor(primary) == Color.BLACK) {
                flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags = if (dark) {
                flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            } else {
                flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
        window.decorView.systemUiVisibility = flags
    }
}
