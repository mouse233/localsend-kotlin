package io.github.mouse233.localsendkotlin.settings

import android.content.Context
import android.net.Uri
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

    fun deviceType(): String = DeviceType.fromValue(preferences.getString(DEVICE_TYPE_KEY, null)).value

    fun setDeviceType(type: String) {
        preferences.edit().putString(DEVICE_TYPE_KEY, DeviceType.fromValue(type).value).apply()
    }

    fun deviceModel(): String = preferences.getString(DEVICE_MODEL_KEY, null)?.trim().orEmpty()
        .ifBlank { defaultDeviceModel() }

    fun saveDeviceModel(model: String) {
        val normalized = model.trim()
        preferences.edit().apply {
            if (normalized.isBlank()) remove(DEVICE_MODEL_KEY) else putString(DEVICE_MODEL_KEY, normalized)
        }.apply()
    }

    fun hideIpv6BindAddresses(): Boolean = preferences.getBoolean(HIDE_IPV6_BIND_ADDRESSES_KEY, false)

    fun setHideIpv6BindAddresses(enabled: Boolean) {
        preferences.edit().putBoolean(HIDE_IPV6_BIND_ADDRESSES_KEY, enabled).apply()
    }

    fun keepScreenAwakeDuringTransfer(): Boolean = preferences.getBoolean(KEEP_SCREEN_AWAKE_KEY, false)

    fun setKeepScreenAwakeDuringTransfer(enabled: Boolean) {
        preferences.edit().putBoolean(KEEP_SCREEN_AWAKE_KEY, enabled).apply()
    }

    fun themeColor(): String = ThemeColorPreset.fromId(preferences.getString(THEME_COLOR_KEY, null)).id

    fun setThemeColor(id: String) {
        preferences.edit().putString(THEME_COLOR_KEY, ThemeColorPreset.fromId(id).id).apply()
    }

    fun language(): String = preferences.getString(LANGUAGE_KEY, AppLocale.SYSTEM) ?: AppLocale.SYSTEM
    fun setLanguage(language: String) = preferences.edit().putString(LANGUAGE_KEY, language).apply()

    fun createChecksums(): Boolean = preferences.getBoolean(CREATE_CHECKSUMS_KEY, true)

    fun setCreateChecksums(enabled: Boolean) {
        preferences.edit().putBoolean(CREATE_CHECKSUMS_KEY, enabled).apply()
    }

    fun autoSaveReceivedFiles(): Boolean = preferences.getBoolean(AUTO_SAVE_KEY, false)
    fun setAutoSaveReceivedFiles(enabled: Boolean) = preferences.edit().putBoolean(AUTO_SAVE_KEY, enabled).apply()

    /** A non-blank PIN enables the LocalSend prepare-upload PIN gate. */
    fun receivePin(): String? = preferences.getString(RECEIVE_PIN_KEY, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setReceivePin(pin: String) {
        preferences.edit().putString(RECEIVE_PIN_KEY, pin.trim()).apply()
    }

    fun clearReceivePin() = preferences.edit().remove(RECEIVE_PIN_KEY).apply()

    fun receiveDirectoryUri(): Uri? = preferences.getString(RECEIVE_DIRECTORY_URI_KEY, null)?.let(Uri::parse)
    fun receiveDirectoryName(): String? = preferences.getString(RECEIVE_DIRECTORY_NAME_KEY, null)
    fun saveReceiveDirectory(uri: Uri, name: String) = preferences.edit()
        .putString(RECEIVE_DIRECTORY_URI_KEY, uri.toString())
        .putString(RECEIVE_DIRECTORY_NAME_KEY, name)
        .apply()
    fun clearReceiveDirectory() = preferences.edit()
        .remove(RECEIVE_DIRECTORY_URI_KEY)
        .remove(RECEIVE_DIRECTORY_NAME_KEY)
        .apply()

    fun saveReceiveHistory(): Boolean = preferences.getBoolean(SAVE_HISTORY_KEY, true)
    fun setSaveReceiveHistory(enabled: Boolean) = preferences.edit().putBoolean(SAVE_HISTORY_KEY, enabled).apply()

    fun verifyReceivedChecksums(): Boolean = preferences.getBoolean(VERIFY_RECEIVED_CHECKSUMS_KEY, true)
    fun setVerifyReceivedChecksums(enabled: Boolean) = preferences.edit().putBoolean(VERIFY_RECEIVED_CHECKSUMS_KEY, enabled).apply()

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

    /** Null means the first-run automatic interface selection is still active. */
    fun networkInterfaceSelection(): Set<String>? = if (preferences.contains(NETWORK_INTERFACES_KEY)) {
        preferences.getStringSet(NETWORK_INTERFACES_KEY, emptySet())?.toSet() ?: emptySet()
    } else {
        null
    }

    fun setNetworkInterfaceSelection(names: Set<String>) {
        preferences.edit().putStringSet(NETWORK_INTERFACES_KEY, names.toSet()).apply()
    }

    fun plainHttpFingerprint(): String = preferences.getString(HTTP_FINGERPRINT_KEY, null) ?: UUID.randomUUID().toString().also {
        preferences.edit().putString(HTTP_FINGERPRINT_KEY, it).apply()
    }

    private fun defaultDeviceName(): String = Build.MODEL.ifBlank { "Android" }

    private fun defaultDeviceModel(): String = Build.MANUFACTURER.ifBlank { Build.MODEL.ifBlank { "Android" } }

    private fun isIpv4Multicast(address: String): Boolean {
        val parts = address.split('.')
        if (parts.size != 4) return false
        val values = parts.map { it.toIntOrNull() ?: return false }
        return values.all { it in 0..255 } && values.first() in 224..239
    }

    private companion object {
        const val PREFERENCES_NAME = "app_settings"
        const val DEVICE_NAME_KEY = "device_name"
        const val DEVICE_TYPE_KEY = "device_type"
        const val DEVICE_MODEL_KEY = "device_model"
        const val HIDE_IPV6_BIND_ADDRESSES_KEY = "hide_ipv6_bind_addresses"
        const val KEEP_SCREEN_AWAKE_KEY = "keep_screen_awake_during_transfer"
        const val THEME_COLOR_KEY = "theme_color"
        const val LANGUAGE_KEY = "language"
        const val CREATE_CHECKSUMS_KEY = "create_checksums"
        const val AUTO_SAVE_KEY = "auto_save_received_files"
        const val RECEIVE_PIN_KEY = "receive_pin"
        const val RECEIVE_DIRECTORY_URI_KEY = "receive_directory_uri"
        const val RECEIVE_DIRECTORY_NAME_KEY = "receive_directory_name"
        const val SAVE_HISTORY_KEY = "save_receive_history"
        const val VERIFY_RECEIVED_CHECKSUMS_KEY = "verify_received_checksums"
        const val SERVER_ENABLED_KEY = "server_enabled"
        const val PORT_KEY = "port"
        const val ENCRYPTION_KEY = "encryption_enabled"
        const val MULTICAST_ADDRESS_KEY = "multicast_address"
        const val NETWORK_INTERFACES_KEY = "network_interfaces"
        const val HTTP_FINGERPRINT_KEY = "http_fingerprint"
    }
}
