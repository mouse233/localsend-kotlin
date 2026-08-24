package io.github.mouse233.localsendkotlin.settings

import android.content.Context
import android.os.Build
import io.github.mouse233.localsendkotlin.protocol.LocalSendProtocol
import java.util.UUID

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

    fun autoSaveReceivedFiles(): Boolean = preferences.getBoolean(AUTO_SAVE_KEY, false)
    fun setAutoSaveReceivedFiles(enabled: Boolean) = preferences.edit().putBoolean(AUTO_SAVE_KEY, enabled).apply()

    fun saveReceiveHistory(): Boolean = preferences.getBoolean(SAVE_HISTORY_KEY, true)
    fun setSaveReceiveHistory(enabled: Boolean) = preferences.edit().putBoolean(SAVE_HISTORY_KEY, enabled).apply()

    fun serverEnabled(): Boolean = preferences.getBoolean(SERVER_ENABLED_KEY, true)
    fun setServerEnabled(enabled: Boolean) = preferences.edit().putBoolean(SERVER_ENABLED_KEY, enabled).apply()

    fun port(): Int = preferences.getInt(PORT_KEY, LocalSendProtocol.DEFAULT_PORT).takeIf { it in 1..65535 }
        ?: LocalSendProtocol.DEFAULT_PORT
    fun setPort(port: Int) = preferences.edit().putInt(PORT_KEY, port).apply()

    fun encryptionEnabled(): Boolean = preferences.getBoolean(ENCRYPTION_KEY, true)
    fun setEncryptionEnabled(enabled: Boolean) = preferences.edit().putBoolean(ENCRYPTION_KEY, enabled).apply()

    fun multicastAddress(): String = preferences.getString(MULTICAST_ADDRESS_KEY, LocalSendProtocol.MULTICAST_ADDRESS)
        ?.takeIf(::isIpv4Multicast) ?: LocalSendProtocol.MULTICAST_ADDRESS
    fun setMulticastAddress(address: String): Boolean {
        val normalized = address.trim()
        if (!isIpv4Multicast(normalized)) return false
        preferences.edit().putString(MULTICAST_ADDRESS_KEY, normalized).apply()
        return true
    }

    fun plainHttpFingerprint(): String = preferences.getString(HTTP_FINGERPRINT_KEY, null) ?: UUID.randomUUID().toString().also {
        preferences.edit().putString(HTTP_FINGERPRINT_KEY, it).apply()
    }

    private fun defaultDeviceName(): String = Build.MODEL.ifBlank { "Android" }

    private fun isIpv4Multicast(address: String): Boolean {
        val parts = address.split('.')
        if (parts.size != 4) return false
        val values = parts.map { it.toIntOrNull() ?: return false }
        return values.all { it in 0..255 } && values.first() in 224..239
    }

    private companion object {
        const val PREFERENCES_NAME = "app_settings"
        const val DEVICE_NAME_KEY = "device_name"
        const val CREATE_CHECKSUMS_KEY = "create_checksums"
        const val AUTO_SAVE_KEY = "auto_save_received_files"
        const val SAVE_HISTORY_KEY = "save_receive_history"
        const val SERVER_ENABLED_KEY = "server_enabled"
        const val PORT_KEY = "port"
        const val ENCRYPTION_KEY = "encryption_enabled"
        const val MULTICAST_ADDRESS_KEY = "multicast_address"
        const val HTTP_FINGERPRINT_KEY = "http_fingerprint"
    }
}
