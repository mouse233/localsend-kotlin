package io.github.mouse233.localsendkotlin.settings

import io.github.mouse233.localsendkotlin.R

/** Stable preset identifiers and their presentation resources. */
enum class ThemeColorPreset(
    val id: String,
    val labelRes: Int,
    val primaryColorRes: Int,
    val pressedColorRes: Int
) {
    RED("red", R.string.theme_color_red, R.color.theme_red, R.color.theme_red_pressed),
    PINK("pink", R.string.theme_color_pink, R.color.theme_pink, R.color.theme_pink_pressed),
    PURPLE("purple", R.string.theme_color_purple, R.color.theme_purple, R.color.theme_purple_pressed),
    DEEP_PURPLE("deep_purple", R.string.theme_color_deep_purple, R.color.theme_deep_purple, R.color.theme_deep_purple_pressed),
    INDIGO("indigo", R.string.theme_color_indigo, R.color.theme_indigo, R.color.theme_indigo_pressed),
    BLUE("blue", R.string.theme_color_blue, R.color.theme_blue, R.color.theme_blue_pressed),
    LIGHT_BLUE("light_blue", R.string.theme_color_light_blue, R.color.theme_light_blue, R.color.theme_light_blue_pressed),
    CYAN("cyan", R.string.theme_color_cyan, R.color.theme_cyan, R.color.theme_cyan_pressed),
    TEAL("teal", R.string.theme_color_teal, R.color.theme_teal, R.color.theme_teal_pressed),
    GREEN("green", R.string.theme_color_green, R.color.theme_green, R.color.theme_green_pressed),
    LIGHT_GREEN("light_green", R.string.theme_color_light_green, R.color.theme_light_green, R.color.theme_light_green_pressed),
    LIME("lime", R.string.theme_color_lime, R.color.theme_lime, R.color.theme_lime_pressed),
    YELLOW("yellow", R.string.theme_color_yellow, R.color.theme_yellow, R.color.theme_yellow_pressed),
    AMBER("amber", R.string.theme_color_amber, R.color.theme_amber, R.color.theme_amber_pressed),
    ORANGE("orange", R.string.theme_color_orange, R.color.theme_orange, R.color.theme_orange_pressed),
    DEEP_ORANGE("deep_orange", R.string.theme_color_deep_orange, R.color.theme_deep_orange, R.color.theme_deep_orange_pressed),
    BROWN("brown", R.string.theme_color_brown, R.color.theme_brown, R.color.theme_brown_pressed),
    GREY("grey", R.string.theme_color_grey, R.color.theme_grey, R.color.theme_grey_pressed),
    BLUE_GREY("blue_grey", R.string.theme_color_blue_grey, R.color.theme_blue_grey, R.color.theme_blue_grey_pressed),
    BLACK("black", R.string.theme_color_black, R.color.theme_black, R.color.theme_black_pressed);

    companion object {
        fun fromId(id: String?): ThemeColorPreset = values().firstOrNull { it.id == id } ?: BLUE_GREY
    }
}
