package io.github.mouse233.localsendkotlin

import android.app.Application
import io.github.mouse233.localsendkotlin.settings.AppLocale

class LocalSendApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLocale.apply(this)
    }
}
