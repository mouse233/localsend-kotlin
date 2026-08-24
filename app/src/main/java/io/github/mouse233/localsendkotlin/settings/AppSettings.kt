package io.github.mouse233.localsendkotlin.settings

import android.content.Context
import android.os.Build

/** Persistent user choices that affect LocalSend identity and transfers. */
class AppSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun deviceName(): String = preferences.getString(DEVICE_NAME_KEY, null)?.trim().orEmpty()
        .ifBlank { defaultDeviceName() }

    fun saveDeviceName(name: String) {
        val normalized = name.trim()
        preferences.edit().apply {
            if (normalized.isBlank()) remove(DEVICE_NAME_KEY) else putString(DEVICE_NAME_KEY, normalized)
        }.apply()
    }

    fun createChecksums(): Boolean = preferences.getBoolean(CREATE_CHECKSUMS_KEY, true)

    fun setCreateChecksums(enabled: Boolean) {
        preferences.edit().putBoolean(CREATE_CHECKSUMS_KEY, enabled).apply()
    }

    private fun defaultDeviceName(): String = Build.MODEL.ifBlank { "Android" }

    private companion object {
        const val PREFERENCES_NAME = "app_settings"
        const val DEVICE_NAME_KEY = "device_name"
        const val CREATE_CHECKSUMS_KEY = "create_checksums"
    }
}
