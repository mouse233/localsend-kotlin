package io.github.mouse233.localsendkotlin.settings

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import java.util.Locale

object AppLocale {
    const val SYSTEM = ""
    const val CHINESE = "zh"
    const val ENGLISH = "en"

    fun wrap(context: Context, language: String = AppSettings(context).language()): Context {
        val locale = localeFor(language)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }

    @Suppress("DEPRECATION")
    private fun localeFor(language: String): Locale {
        if (language.isNotBlank()) return Locale(language)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Resources.getSystem().configuration.locales[0]
        } else {
            Resources.getSystem().configuration.locale
        }
    }
}
