package io.github.mouse233.localsendkotlin.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.core.graphics.drawable.DrawableCompat
import io.github.mouse233.localsendkotlin.R
import io.github.mouse233.localsendkotlin.settings.AppSettings
import io.github.mouse233.localsendkotlin.settings.DarkModePreference
import io.github.mouse233.localsendkotlin.settings.ThemeColorPreset

object ThemeColors {
    /** Returns whether an Activity must be recreated to switch between palettes. */
    internal fun needsActivityRecreate(previousDarkMode: Boolean?, currentDarkMode: Boolean): Boolean =
        previousDarkMode != null && previousDarkMode != currentDarkMode

    @Suppress("DEPRECATION")
    fun primaryColor(context: Context): Int = color(context, ThemeColorPreset.fromId(AppSettings(context).themeColor()))

    @Suppress("DEPRECATION")
    fun color(context: Context, preset: ThemeColorPreset): Int = context.resources.getColor(preset.primaryColorRes)

    @Suppress("DEPRECATION")
    fun pressedColor(context: Context): Int = context.resources.getColor(
        ThemeColorPreset.fromId(AppSettings(context).themeColor()).pressedColorRes
    )

    fun isDark(context: Context): Boolean = when (DarkModePreference.fromId(AppSettings(context).darkMode())) {
        DarkModePreference.ENABLED -> true
        DarkModePreference.DISABLED -> false
        DarkModePreference.FOLLOW_SYSTEM ->
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    @Suppress("DEPRECATION")
    fun backgroundColor(context: Context): Int = context.resources.getColor(
        if (isDark(context)) R.color.dark_window_background else R.color.window_background
    )

    @Suppress("DEPRECATION")
    fun primaryTextColor(context: Context): Int = context.resources.getColor(
        if (isDark(context)) R.color.dark_primary_text else R.color.primary_text
    )

    @Suppress("DEPRECATION")
    fun apply(activity: Activity) {
        val root = activity.findViewById<View>(android.R.id.content) ?: return
        apply(root)
        activity.window.statusBarColor = primaryColor(activity)
    }

    fun apply(view: View) {
        applyToView(view, palette(view.context), false)
    }

    fun apply(dialog: AlertDialog) {
        val palette = palette(dialog.context)
        val window = dialog.window ?: return
        if (palette.dark) window.setBackgroundDrawable(ColorDrawable(palette.dialogBackground))
        constrainDialogWidth(window)
        val root = window.decorView
        applyDialogContent(root, palette)
        applyDialogButtons(root, palette.primary)
        root.post {
            applyDialogContent(root, palette)
            applyDialogButtons(root, palette.primary)
            constrainDialogWidth(window)
        }
    }

    fun foregroundColor(background: Int): Int {
        val brightness = (Color.red(background) * 299 + Color.green(background) * 587 + Color.blue(background) * 114) / 1000
        return if (brightness > 160) Color.BLACK else Color.WHITE
    }

    private data class Palette(
        val primary: Int,
        val foreground: Int,
        val dark: Boolean,
        val dayWindowBackground: Int,
        val windowBackground: Int,
        val dialogBackground: Int,
        val darkSurface: Int,
        val dayItemBorder: Int,
        val itemBorder: Int,
        val dayPrimaryText: Int,
        val primaryText: Int,
        val daySecondaryText: Int,
        val secondaryText: Int,
        val presetColors: Set<Int>,
        val buttonTint: ColorStateList,
        val switchThumbTint: ColorStateList,
        val switchTrackTint: ColorStateList
    )

    @Suppress("DEPRECATION")
    private fun palette(context: Context): Palette {
        val resources = context.resources
        val primary = primaryColor(context)
        val pressed = pressedColor(context)
        val dark = isDark(context)
        val dayWindowBackground = resources.getColor(R.color.window_background)
        val dayItemBorder = resources.getColor(R.color.item_border)
        val dayPrimaryText = resources.getColor(R.color.primary_text)
        val daySecondaryText = resources.getColor(R.color.secondary_text)
        val presetColors = ThemeColorPreset.values().mapTo(
            mutableSetOf(resources.getColor(R.color.brand_primary))
        ) { color(context, it) }
        val foreground = foregroundColor(primary)
        return Palette(
            primary = primary,
            foreground = foreground,
            dark = dark,
            dayWindowBackground = dayWindowBackground,
            windowBackground = if (dark) resources.getColor(R.color.dark_window_background) else dayWindowBackground,
            dialogBackground = resources.getColor(R.color.dark_dialog_background),
            darkSurface = resources.getColor(R.color.dark_surface),
            dayItemBorder = dayItemBorder,
            itemBorder = if (dark) resources.getColor(R.color.dark_item_border) else dayItemBorder,
            dayPrimaryText = dayPrimaryText,
            primaryText = if (dark) resources.getColor(R.color.dark_primary_text) else dayPrimaryText,
            daySecondaryText = daySecondaryText,
            secondaryText = if (dark) resources.getColor(R.color.dark_secondary_text) else daySecondaryText,
            presetColors = presetColors,
            buttonTint = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
                intArrayOf(pressed, primary)
            ),
            switchThumbTint = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(primary, if (dark) 0xFF9E9E9E.toInt() else Color.LTGRAY)
            ),
            switchTrackTint = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(withAlpha(primary, 0x66), if (dark) 0x669E9E9E else 0x55000000)
            )
        )
    }

    private fun applyToView(view: View, palette: Palette, inPrimarySurface: Boolean) {
        val background = view.background
        val hasPrimaryBackground = background is ColorDrawable && background.color in palette.presetColors
        val hasWindowBackground = background is ColorDrawable && background.color == palette.dayWindowBackground
        val hasItemBorder = background is ColorDrawable && background.color == palette.dayItemBorder
        val isPendingSendBar = view.id == R.id.pending_send_bar
        val isPrimarySurface = inPrimarySurface || hasPrimaryBackground

        if (view is TextView) {
            val textColor = view.textColors.defaultColor
            when {
                isPrimarySurface && (textColor == Color.WHITE || textColor == Color.BLACK) -> view.setTextColor(palette.foreground)
                palette.dark && (textColor == palette.dayPrimaryText || textColor == Color.BLACK) -> view.setTextColor(palette.primaryText)
                palette.dark && textColor == palette.daySecondaryText -> view.setTextColor(palette.secondaryText)
                textColor in palette.presetColors -> view.setTextColor(if (palette.dark) palette.primary else if (palette.foreground == Color.BLACK) Color.BLACK else palette.primary)
            }
        }
        if (view is ImageView) {
            val imageColor = view.imageTintList?.defaultColor
            when {
                palette.dark && imageColor == palette.dayPrimaryText -> view.imageTintList = ColorStateList.valueOf(palette.primaryText)
                palette.dark && imageColor == palette.daySecondaryText -> view.imageTintList = ColorStateList.valueOf(palette.secondaryText)
                (isPrimarySurface || view.id in COLORED_IMAGE_BUTTON_IDS) && (imageColor == Color.WHITE || imageColor == Color.BLACK) -> {
                    view.imageTintList = ColorStateList.valueOf(palette.foreground)
                }
            }
        }
        when {
            hasPrimaryBackground -> view.setBackgroundColor(palette.primary)
            palette.dark && hasWindowBackground -> view.setBackgroundColor(palette.windowBackground)
            palette.dark && hasItemBorder -> view.setBackgroundColor(palette.itemBorder)
            palette.dark && background is GradientDrawable && (view.id == View.NO_ID || isPendingSendBar) -> {
                background.mutate()
                background.setColor(palette.darkSurface)
                background.setStroke(dp(view.context, 1), palette.itemBorder)
            }
        }
        when (view) {
            is Switch -> tintSwitch(view, palette.switchThumbTint, palette.switchTrackTint)
            is CompoundButton -> tintCompoundButton(view, palette)
            is Button -> {
                view.setTextColor(palette.foreground)
                tintBackground(view, palette.buttonTint)
            }
            is ImageButton -> if (view.id in COLORED_IMAGE_BUTTON_IDS) tintBackground(view, palette.buttonTint)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) applyToView(view.getChildAt(index), palette, isPrimarySurface)
        }
    }

    private fun applyDialogContent(view: View, palette: Palette) {
        val background = view.background
        if (palette.dark && background is ColorDrawable &&
            (background.color == Color.WHITE || background.color == palette.dayWindowBackground)
        ) {
            view.setBackgroundColor(palette.dialogBackground)
        }
        if (view is TextView) {
            val textColor = view.textColors.defaultColor
            when {
                palette.dark && (textColor == palette.daySecondaryText) -> view.setTextColor(palette.secondaryText)
                palette.dark -> view.setTextColor(palette.primaryText)
            }
        }
        if (view is CheckedTextView) {
            val checkMarkTint = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(palette.primary, palette.secondaryText)
            )
            view.checkMarkTintList = checkMarkTint
            view.checkMarkDrawable?.mutate()?.let { drawable ->
                DrawableCompat.setTintList(drawable, checkMarkTint)
                view.checkMarkDrawable = drawable
            }
        }
        if (view is CompoundButton && view !is Switch) tintCompoundButton(view, palette)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) applyDialogContent(view.getChildAt(index), palette)
        }
    }

    private fun applyDialogButtons(view: View, primary: Int) {
        if (view is Button) view.setTextColor(primary)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) applyDialogButtons(view.getChildAt(index), primary)
        }
    }

    private fun constrainDialogWidth(window: Window) {
        val context = window.context
        // Keep a visible edge margin while giving compact dialogs more usable width.
        val horizontalMargin = dp(context, 24)
        val availableWidth = (context.resources.displayMetrics.widthPixels - horizontalMargin * 2).coerceAtLeast(1)
        val maxWidth = dp(context, 560)
        window.setLayout(kotlin.math.min(availableWidth, maxWidth), WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun tintBackground(view: View, tint: ColorStateList) {
        view.background?.mutate()?.let { drawable ->
            DrawableCompat.setTintList(drawable, tint)
            view.background = drawable
        }
        view.backgroundTintList = tint
    }

    private fun tintSwitch(view: Switch, thumbTint: ColorStateList, trackTint: ColorStateList) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.thumbTintList = thumbTint
            view.trackTintList = trackTint
        }
        view.thumbDrawable?.mutate()?.let { drawable ->
            DrawableCompat.setTintList(drawable, thumbTint)
            view.thumbDrawable = drawable
        }
        view.trackDrawable?.mutate()?.let { drawable ->
            DrawableCompat.setTintList(drawable, trackTint)
            view.trackDrawable = drawable
        }
    }

    private fun tintCompoundButton(view: CompoundButton, palette: Palette) {
        view.buttonTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(palette.primary, palette.secondaryText)
        )
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private val COLORED_IMAGE_BUTTON_IDS = setOf(
        R.id.select_file_fab,
        R.id.content_action_file,
        R.id.content_action_folder,
        R.id.content_action_media,
        R.id.content_action_text,
        R.id.content_action_clipboard
    )
}
