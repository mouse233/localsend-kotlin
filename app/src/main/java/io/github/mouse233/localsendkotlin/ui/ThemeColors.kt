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
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.core.graphics.drawable.DrawableCompat
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
        val presetColors = ThemeColorPreset.values().mapTo(
            mutableSetOf(activity.resources.getColor(R.color.brand_primary))
        ) { color(activity, it) }
        val foreground = foregroundColor(primary)
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
        activity.window.statusBarColor = primary
        applyToView(root, presetColors, primary, foreground, tint, switchThumbTint, switchTrackTint, false)
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
        presetColors: Set<Int>,
        primary: Int,
        foreground: Int,
        tint: ColorStateList,
        switchThumbTint: ColorStateList,
        switchTrackTint: ColorStateList,
        inPrimarySurface: Boolean
    ) {
        val background = view.background
        val hasPrimaryBackground = background is ColorDrawable && background.color in presetColors
        val isPrimarySurface = inPrimarySurface || hasPrimaryBackground
        if (view is TextView) {
            val textColor = view.textColors.defaultColor
            when {
                isPrimarySurface && (textColor == Color.WHITE || textColor == Color.BLACK) -> {
                    view.setTextColor(foreground)
                }
                textColor in presetColors -> view.setTextColor(if (foreground == Color.BLACK) foreground else primary)
            }
        }
        if (view is ImageView &&
            (isPrimarySurface || view.id in COLORED_IMAGE_BUTTON_IDS) &&
            view.imageTintList?.defaultColor?.let { it == Color.WHITE || it == Color.BLACK } == true
        ) {
            view.imageTintList = ColorStateList.valueOf(foreground)
        }
        if (hasPrimaryBackground) {
            view.setBackgroundColor(primary)
        }
        when (view) {
            is Switch -> {
                tintSwitch(view, switchThumbTint, switchTrackTint)
            }
            is Button -> {
                view.setTextColor(foreground)
                tintBackground(view, tint)
            }
            is ImageButton -> if (view.id in COLORED_IMAGE_BUTTON_IDS) tintBackground(view, tint)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyToView(
                    view.getChildAt(index), presetColors, primary, foreground, tint,
                    switchThumbTint, switchTrackTint, isPrimarySurface
                )
            }
        }
    }

    fun foregroundColor(background: Int): Int {
        val brightness = (Color.red(background) * 299 + Color.green(background) * 587 + Color.blue(background) * 114) / 1000
        return if (brightness > 160) Color.BLACK else Color.WHITE
    }

    private fun tintBackground(view: View, tint: ColorStateList) {
        view.background?.mutate()?.let { drawable ->
            DrawableCompat.setTintList(drawable, tint)
            view.background = drawable
        }
        view.backgroundTintList = tint
    }

    private fun tintSwitch(view: Switch, thumbTint: ColorStateList, trackTint: ColorStateList) {
        view.thumbTintList = thumbTint
        view.trackTintList = trackTint
        view.thumbDrawable?.mutate()?.let { drawable ->
            DrawableCompat.setTintList(drawable, thumbTint)
            view.thumbDrawable = drawable
        }
        view.trackDrawable?.mutate()?.let { drawable ->
            DrawableCompat.setTintList(drawable, trackTint)
            view.trackDrawable = drawable
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
