package io.github.mouse233.localsendkotlin.settings

import io.github.mouse233.localsendkotlin.R

/** How the app chooses between its light and dark palettes. */
enum class DarkModePreference(
    val id: String,
    val labelRes: Int
) {
    FOLLOW_SYSTEM("follow_system", R.string.dark_mode_follow_system),
    ENABLED("enabled", R.string.dark_mode_enabled),
    DISABLED("disabled", R.string.dark_mode_disabled);

    companion object {
        fun fromId(id: String?): DarkModePreference =
            values().firstOrNull { it.id == id } ?: FOLLOW_SYSTEM
    }
}
