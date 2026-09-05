package io.github.mouse233.localsendkotlin.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import com.google.gson.Gson
import io.github.mouse233.localsendkotlin.model.FavoriteDevice
import io.github.mouse233.localsendkotlin.model.RemoteDevice
import io.github.mouse233.localsendkotlin.protocol.LocalSendProtocol
import java.util.UUID

/** Persistent user choices that affect LocalSend identity and transfers. */
class AppSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

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

    fun darkMode(): String = DarkModePreference.fromId(preferences.getString(DARK_MODE_KEY, null)).id

    fun setDarkMode(mode: String) {
        preferences.edit().putString(DARK_MODE_KEY, DarkModePreference.fromId(mode).id).apply()
    }

    fun language(): String = when (preferences.getString(LANGUAGE_KEY, AppLocale.SYSTEM)) {
        AppLocale.CHINESE -> AppLocale.CHINESE
        AppLocale.ENGLISH -> AppLocale.ENGLISH
        else -> AppLocale.SYSTEM
    }

    fun setLanguage(language: String) = preferences.edit().putString(
        LANGUAGE_KEY,
        when (language) {
            AppLocale.CHINESE -> AppLocale.CHINESE
            AppLocale.ENGLISH -> AppLocale.ENGLISH
            else -> AppLocale.SYSTEM
        }
    ).apply()

    fun createChecksums(): Boolean = preferences.getBoolean(CREATE_CHECKSUMS_KEY, true)

    fun setCreateChecksums(enabled: Boolean) {
        preferences.edit().putBoolean(CREATE_CHECKSUMS_KEY, enabled).apply()
    }

    fun autoSaveReceivedFiles(): Boolean = preferences.getBoolean(AUTO_SAVE_KEY, false)
    fun setAutoSaveReceivedFiles(enabled: Boolean) = preferences.edit().putBoolean(AUTO_SAVE_KEY, enabled).apply()

    fun autoSaveFavoriteReceivedFiles(): Boolean = preferences.getBoolean(AUTO_SAVE_FAVORITES_KEY, false)
    fun setAutoSaveFavoriteReceivedFiles(enabled: Boolean) = preferences.edit().putBoolean(AUTO_SAVE_FAVORITES_KEY, enabled).apply()

    fun favoriteDevices(): List<FavoriteDevice> {
        val raw = preferences.getString(FAVORITES_KEY, null) ?: return emptyList()
        return runCatching {
            gson.fromJson(raw, Array<FavoriteDevice>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())
    }

    fun isFavorite(fingerprint: String): Boolean = favoriteDevices().any {
        it.fingerprint.equals(fingerprint, ignoreCase = true)
    }

    /** Adds a device when absent and removes it when already favorited. */
    fun toggleFavorite(device: RemoteDevice): Boolean {
        val favorites = favoriteDevices().toMutableList()
        val existingIndex = favorites.indexOfFirst { it.matches(device) }
        return if (existingIndex >= 0) {
            favorites.removeAt(existingIndex)
            saveFavoriteDevices(favorites)
            false
        } else {
            favorites += FavoriteDevice(device.fingerprint, device.alias, device.address, device.port, device.protocol)
            saveFavoriteDevices(favorites)
            true
        }
    }

    /** Adds a manually resolved device without toggling an existing favorite off. */
    fun addFavorite(device: RemoteDevice): Boolean {
        val favorites = favoriteDevices().toMutableList()
        if (favorites.any { it.matches(device) }) return false
        favorites += FavoriteDevice(
            fingerprint = device.fingerprint,
            alias = device.alias,
            address = device.address,
            port = device.port,
            protocol = device.protocol,
            customEndpoint = true
        )
        saveFavoriteDevices(favorites)
        return true
    }

    /** Refreshes endpoint metadata while retaining the fingerprint-based identity. */
    fun refreshFavorite(device: RemoteDevice) {
        val favorites = favoriteDevices().toMutableList()
        val index = favorites.indexOfFirst { it.matches(device) }
        if (index < 0) return
        val refreshed = favorites[index].refreshedFrom(device)
        if (refreshed != favorites[index]) saveFavoriteDevices(favorites.apply { set(index, refreshed) })
    }

    /** Updates the user-editable fields of a favorite by its certificate identity. */
    fun updateFavorite(
        favorite: FavoriteDevice,
        alias: String,
        address: String,
        port: Int,
        customEndpoint: Boolean = false
    ): Boolean {
        val favorites = favoriteDevices().toMutableList()
        val index = favorites.indexOfFirst {
            it.fingerprint.equals(favorite.fingerprint, ignoreCase = true)
        }
        if (index < 0) return false
        favorites[index] = favorites[index].copy(
            alias = alias.trim(),
            address = address.trim(),
            port = port,
            customAlias = alias.trim() != favorites[index].alias || favorites[index].customAlias,
            customEndpoint = customEndpoint
        )
        saveFavoriteDevices(favorites)
        return true
    }

    /** Removes a favorite by its certificate identity. */
    fun removeFavorite(fingerprint: String): Boolean {
        val favorites = favoriteDevices().toMutableList()
        val removed = favorites.removeAll { it.fingerprint.equals(fingerprint, ignoreCase = true) }
        if (removed) saveFavoriteDevices(favorites)
        return removed
    }

    private fun saveFavoriteDevices(favorites: List<FavoriteDevice>) {
        preferences.edit().putString(FAVORITES_KEY, gson.toJson(favorites)).apply()
    }

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
        const val DARK_MODE_KEY = "dark_mode"
        const val LANGUAGE_KEY = "language"
        const val CREATE_CHECKSUMS_KEY = "create_checksums"
        const val AUTO_SAVE_KEY = "auto_save_received_files"
        const val AUTO_SAVE_FAVORITES_KEY = "auto_save_favorite_received_files"
        const val FAVORITES_KEY = "favorite_devices"
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
