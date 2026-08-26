package io.github.mouse233.localsendkotlin.settings

import io.github.mouse233.localsendkotlin.R

/** Stable preset identifiers and their presentation resources. */
enum class ThemeColorPreset(
    val id: String,
    val labelRes: Int,
    val primaryColorRes: Int,
    val pressedColorRes: Int
) {
    BLUE("blue", R.string.theme_color_blue, R.color.brand_primary, R.color.brand_primary_pressed),
    GREEN("green", R.string.theme_color_green, R.color.theme_green, R.color.theme_green_pressed),
    PURPLE("purple", R.string.theme_color_purple, R.color.theme_purple, R.color.theme_purple_pressed),
    ORANGE("orange", R.string.theme_color_orange, R.color.theme_orange, R.color.theme_orange_pressed),
    TEAL("teal", R.string.theme_color_teal, R.color.theme_teal, R.color.theme_teal_pressed);

    companion object {
        fun fromId(id: String?): ThemeColorPreset = values().firstOrNull { it.id == id } ?: BLUE
    }
}
