package io.github.mouse233.localsendkotlin.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import io.github.mouse233.localsendkotlin.R
import io.github.mouse233.localsendkotlin.settings.AppSettings
import io.github.mouse233.localsendkotlin.settings.ThemeColorPreset

object ThemeColors {
    @Suppress("DEPRECATION")
    fun primaryColor(context: Context): Int = color(context, ThemeColorPreset.fromId(AppSettings(context).themeColor()))

    @Suppress("DEPRECATION")
    fun color(context: Context, preset: ThemeColorPreset): Int = context.resources.getColor(preset.primaryColorRes)

    @Suppress("DEPRECATION")
    fun pressedColor(context: Context): Int = context.resources.getColor(
        ThemeColorPreset.fromId(AppSettings(context).themeColor()).pressedColorRes
    )

    @Suppress("DEPRECATION")
    fun apply(activity: Activity) {
        val root = activity.findViewById<View>(android.R.id.content) ?: return
        val primary = primaryColor(activity)
        val pressed = pressedColor(activity)
        val originalPrimary = activity.resources.getColor(R.color.brand_primary)
        val tint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(pressed, primary)
        )
        val switchThumbTint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(primary, Color.LTGRAY)
        )
        val switchTrackTint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(withAlpha(primary, 0x66), 0x55000000)
        )
        applyToView(root, originalPrimary, primary, tint, switchThumbTint, switchTrackTint)
    }

    fun apply(dialog: AlertDialog) {
        val primary = primaryColor(dialog.context)
        val root = dialog.window?.decorView ?: return
        applyDialogButtons(root, primary)
    }

    private fun applyDialogButtons(view: View, primary: Int) {
        if (view is Button) view.setTextColor(primary)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyDialogButtons(view.getChildAt(index), primary)
            }
        }
    }

    private fun applyToView(
        view: View,
        originalPrimary: Int,
        primary: Int,
        tint: ColorStateList,
        switchThumbTint: ColorStateList,
        switchTrackTint: ColorStateList
    ) {
        if (view is TextView && view.textColors.defaultColor == originalPrimary) {
            view.setTextColor(primary)
        }
        val background = view.background
        if (background is ColorDrawable && background.color == originalPrimary) {
            view.setBackgroundColor(primary)
        }
        when (view) {
            is Button -> view.backgroundTintList = tint
            is Switch -> {
                view.thumbTintList = switchThumbTint
                view.trackTintList = switchTrackTint
            }
            is ImageButton -> if (view.id in COLORED_IMAGE_BUTTON_IDS) view.backgroundTintList = tint
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyToView(view.getChildAt(index), originalPrimary, primary, tint, switchThumbTint, switchTrackTint)
            }
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private val COLORED_IMAGE_BUTTON_IDS = setOf(
        R.id.select_file_fab,
        R.id.content_action_file,
        R.id.content_action_folder,
        R.id.content_action_media,
        R.id.content_action_text,
        R.id.content_action_clipboard
    )
}
