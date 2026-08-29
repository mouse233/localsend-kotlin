package io.github.mouse233.localsendkotlin

import android.app.Activity
import android.content.Context
import io.github.mouse233.localsendkotlin.settings.AppLocale

/** Applies the saved app language before Android inflates an Activity's views. */
abstract class LocalizedActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }
}
