package io.github.mouse233.localsendkotlin.settings

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

object AppLocale {
    const val SYSTEM = ""
    const val CHINESE = "zh"
    const val ENGLISH = "en"

    @Suppress("DEPRECATION")
    fun apply(context: Context, language: String = AppSettings(context).language()) {
        val locale = if (language.isBlank()) Resources.getSystem().configuration.locale else Locale(language)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
    }
}
