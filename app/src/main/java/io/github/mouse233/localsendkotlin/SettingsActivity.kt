package io.github.mouse233.localsendkotlin

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Switch
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import io.github.mouse233.localsendkotlin.settings.AppSettings
import io.github.mouse233.localsendkotlin.settings.AppLocale
import io.github.mouse233.localsendkotlin.settings.DeviceType
import io.github.mouse233.localsendkotlin.settings.ThemeColorPreset
import io.github.mouse233.localsendkotlin.discovery.NetworkInterfaceCatalog
import io.github.mouse233.localsendkotlin.transfer.TransferService
import io.github.mouse233.localsendkotlin.ui.SystemBars
import io.github.mouse233.localsendkotlin.ui.ThemeColors

class SettingsActivity : Activity() {
    private lateinit var settings: AppSettings
    private lateinit var deviceInfoValue: TextView
    private lateinit var languageValue: TextView
    private lateinit var themeColorValue: TextView
    private lateinit var portValue: TextView
    private lateinit var multicastAddressValue: TextView
    private lateinit var networkInterfacesValue: TextView
    private lateinit var receiveDirectoryValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SystemBars.apply(this)
        setContentView(R.layout.activity_settings)
        ThemeColors.apply(this)
        settings = AppSettings(this)
        deviceInfoValue = findViewById(R.id.device_info_value)
        languageValue = findViewById(R.id.language_value)
        themeColorValue = findViewById(R.id.theme_color_value)
        portValue = findViewById(R.id.port_value)
        multicastAddressValue = findViewById(R.id.multicast_address_value)
        networkInterfacesValue = findViewById(R.id.network_interfaces_value)
        receiveDirectoryValue = findViewById(R.id.receive_directory_value)
        refreshValues()
        findViewById<android.view.View>(R.id.settings_back_button).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.language_row).setOnClickListener { showLanguagePicker() }
        findViewById<View>(R.id.theme_color_row).setOnClickListener { showThemeColorPicker() }
        findViewById<android.view.View>(R.id.device_info_row).setOnClickListener { showDeviceInfoDialog() }
        val hideIpv6Switch = findViewById<Switch>(R.id.hide_ipv6_switch).apply {
            isChecked = settings.hideIpv6BindAddresses()
            setOnCheckedChangeListener { _, checked -> settings.setHideIpv6BindAddresses(checked) }
        }
        findViewById<android.view.View>(R.id.hide_ipv6_row).setOnClickListener { hideIpv6Switch.toggle() }
        val keepScreenAwakeSwitch = findViewById<Switch>(R.id.keep_screen_awake_switch).apply {
            isChecked = settings.keepScreenAwakeDuringTransfer()
            setOnCheckedChangeListener { _, checked ->
                settings.setKeepScreenAwakeDuringTransfer(checked)
                notifyTransferServiceOfScreenAwakeChange()
            }
        }
        findViewById<android.view.View>(R.id.keep_screen_awake_row).setOnClickListener { keepScreenAwakeSwitch.toggle() }
        val checksumSwitch = findViewById<Switch>(R.id.checksum_switch).apply {
            isChecked = settings.createChecksums()
            setOnCheckedChangeListener { _, checked -> settings.setCreateChecksums(checked) }
        }
        findViewById<android.view.View>(R.id.checksum_row).setOnClickListener { checksumSwitch.toggle() }
        val autoSaveSwitch = findViewById<Switch>(R.id.auto_save_switch).apply {
            isChecked = settings.autoSaveReceivedFiles()
            setOnCheckedChangeListener { _, checked -> settings.setAutoSaveReceivedFiles(checked) }
        }
        findViewById<android.view.View>(R.id.auto_save_row).setOnClickListener { autoSaveSwitch.toggle() }
        val receivePinSwitch = findViewById<Switch>(R.id.receive_pin_switch).apply {
            isChecked = settings.receivePin() != null
            setOnCheckedChangeListener { _, checked ->
                if (checked) showReceivePinDialog(this) else settings.clearReceivePin()
            }
        }
        findViewById<android.view.View>(R.id.receive_pin_row).setOnClickListener { receivePinSwitch.toggle() }
        findViewById<android.view.View>(R.id.receive_directory_row).setOnClickListener { chooseReceiveDirectory() }
        val saveHistorySwitch = findViewById<Switch>(R.id.save_history_switch).apply {
            isChecked = settings.saveReceiveHistory()
            setOnCheckedChangeListener { _, checked -> settings.setSaveReceiveHistory(checked) }
        }
        findViewById<android.view.View>(R.id.save_history_row).setOnClickListener { saveHistorySwitch.toggle() }
        val verifyReceivedChecksumsSwitch = findViewById<Switch>(R.id.verify_received_checksums_switch).apply {
            isChecked = settings.verifyReceivedChecksums()
            setOnCheckedChangeListener { _, checked -> settings.setVerifyReceivedChecksums(checked) }
        }
        findViewById<android.view.View>(R.id.verify_received_checksums_row).setOnClickListener { verifyReceivedChecksumsSwitch.toggle() }
        val serverSwitch = findViewById<Switch>(R.id.server_switch).apply {
            isChecked = settings.serverEnabled()
            setOnCheckedChangeListener { _, checked ->
                settings.setServerEnabled(checked)
                reloadNetwork()
            }
        }
        findViewById<android.view.View>(R.id.server_row).setOnClickListener { serverSwitch.toggle() }
        findViewById<android.view.View>(R.id.port_row).setOnClickListener { showPortEditor() }
        val encryptionSwitch = findViewById<Switch>(R.id.encryption_switch).apply {
            isChecked = settings.encryptionEnabled()
            setOnCheckedChangeListener { _, checked ->
                settings.setEncryptionEnabled(checked)
                reloadNetwork()
            }
        }
        findViewById<android.view.View>(R.id.encryption_row).setOnClickListener { encryptionSwitch.toggle() }
        findViewById<android.view.View>(R.id.multicast_address_row).setOnClickListener { showMulticastAddressEditor() }
        findViewById<android.view.View>(R.id.network_interfaces_row).setOnClickListener { showNetworkInterfacesPicker() }
        findViewById<TextView>(R.id.version_value).text = BuildConfig.VERSION_NAME
        findViewById<android.view.View>(R.id.version_row).setOnClickListener { openUrl(RELEASES_URL) }
        findViewById<android.view.View>(R.id.changelog_row).setOnClickListener { startActivity(Intent(this, ChangelogActivity::class.java)) }
        findViewById<android.view.View>(R.id.source_code_row).setOnClickListener { openUrl(REPOSITORY_URL) }
        findViewById<android.view.View>(R.id.license_row).setOnClickListener {
            openDocument(R.string.settings_license, LICENSE_FILE)
        }
        findViewById<android.view.View>(R.id.feedback_row).setOnClickListener { openUrl(ISSUES_URL) }
        findViewById<android.view.View>(R.id.third_party_licenses_row).setOnClickListener {
            openDocument(R.string.settings_third_party_licenses, NOTICE_FILE)
        }
    }

    private fun notifyTransferServiceOfScreenAwakeChange() {
        startService(Intent(this, TransferService::class.java).setAction(TransferService.ACTION_REFRESH_SCREEN_AWAKE))
    }

    private fun showDeviceInfoDialog() {
        val content = layoutInflater.inflate(R.layout.dialog_device_info, null)
        val nameInput = content.findViewById<EditText>(R.id.device_info_name_editor).apply {
            setText(settings.deviceName())
            setSelection(length())
            filters = arrayOf(InputFilter.LengthFilter(64))
        }
        val modelInput = content.findViewById<EditText>(R.id.device_info_model_editor).apply {
            setText(settings.deviceModel())
            setSelection(length())
            filters = arrayOf(InputFilter.LengthFilter(64))
        }
        val types = arrayOf(
            DeviceType.MOBILE to R.string.device_type_mobile,
            DeviceType.DESKTOP to R.string.device_type_desktop,
            DeviceType.WEB to R.string.device_type_web,
            DeviceType.HEADLESS to R.string.device_type_headless,
            DeviceType.SERVER to R.string.device_type_server
        )
        val typeSpinner = content.findViewById<Spinner>(R.id.device_info_type_spinner)
        typeSpinner.adapter = ArrayAdapter(this, R.layout.item_device_type_spinner, types.map { getString(it.second) }).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        typeSpinner.setSelection(types.indexOfFirst { it.first.value == settings.deviceType() }.coerceAtLeast(0))

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings_device_information)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .show()
        ThemeColors.apply(dialog)
        val customPanelId = resources.getIdentifier("customPanel", "id", "android")
        if (customPanelId != 0) dialog.findViewById<android.view.View>(customPanelId)?.minimumHeight = 0
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            settings.saveDeviceName(nameInput.text.toString())
            settings.setDeviceType(types[typeSpinner.selectedItemPosition].first.value)
            settings.saveDeviceModel(modelInput.text.toString())
            refreshValues()
            reloadNetwork()
            dialog.dismiss()
        }
    }

    private fun showReceivePinDialog(pinSwitch: Switch) {
        val content = layoutInflater.inflate(R.layout.dialog_edit_device_name, null)
        val input = content.findViewById<EditText>(R.id.device_name_editor).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.pin_input_hint)
        }
        var saved = false
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings_receive_pin_title)
            .setMessage(R.string.settings_receive_pin_message)
            .setView(content)
            .setNegativeButton(android.R.string.cancel) { _, _ -> pinSwitch.isChecked = false }
            .setPositiveButton(R.string.save, null)
            .setOnCancelListener { if (!saved) pinSwitch.isChecked = false }
            .create()
        dialog.show()
        ThemeColors.apply(dialog)
        val customPanelId = resources.getIdentifier("customPanel", "id", "android")
        if (customPanelId != 0) dialog.findViewById<android.view.View>(customPanelId)?.minimumHeight = 0
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val pin = input.text.toString().trim()
            if (pin.isEmpty()) {
                input.error = getString(R.string.invalid_pin)
                return@setOnClickListener
            }
            settings.setReceivePin(pin)
            saved = true
            dialog.dismiss()
        }
    }

    private fun showLanguagePicker() {
        val languages = arrayOf(
            getString(R.string.language_system), getString(R.string.language_chinese), getString(R.string.language_english)
        )
        val codes = arrayOf(AppLocale.SYSTEM, AppLocale.CHINESE, AppLocale.ENGLISH)
        val selected = codes.indexOf(settings.language()).coerceAtLeast(0)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings_language)
            .setSingleChoiceItems(languages, selected) { dialog, which ->
                settings.setLanguage(codes[which])
                AppLocale.apply(this, codes[which])
                dialog.dismiss()
                recreate()
            }
            .show()
        ThemeColors.apply(dialog)
    }

    private fun showThemeColorPicker() {
        val presets = ThemeColorPreset.values()
        val selectedId = settings.themeColor()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings_theme_color)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        presets.forEach { preset ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                minimumHeight = dp(56)
            }
            val swatch = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                    marginEnd = dp(16)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ThemeColors.color(this@SettingsActivity, preset))
                }
                contentDescription = getString(preset.labelRes)
            }
            val label = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = getString(preset.labelRes)
                textSize = 16f
            }
            val radio = RadioButton(this).apply {
                isClickable = false
                isChecked = preset.id == selectedId
                buttonTintList = ColorStateList.valueOf(ThemeColors.color(this@SettingsActivity, preset))
            }
            row.addView(swatch)
            row.addView(label)
            row.addView(radio)
            row.setOnClickListener {
                settings.setThemeColor(preset.id)
                dialog.dismiss()
                recreate()
            }
            content.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))
        }
        dialog.show()
        ThemeColors.apply(dialog)
    }

    private fun showPortEditor() = showEditor(R.string.settings_port, settings.port().toString(), InputType.TYPE_CLASS_NUMBER) { value ->
        val port = value.toIntOrNull()
        if (port == null || port !in 1..65535) {
            Toast.makeText(this, R.string.settings_invalid_port, Toast.LENGTH_SHORT).show()
            return@showEditor false
        }
        settings.setPort(port)
        reloadNetwork()
        refreshValues()
        true
    }

    private fun showMulticastAddressEditor() = showEditor(
        R.string.settings_multicast_address,
        settings.multicastAddress(),
        InputType.TYPE_CLASS_TEXT
    ) { value ->
        if (!settings.setMulticastAddress(value)) {
            Toast.makeText(this, R.string.settings_invalid_multicast_address, Toast.LENGTH_SHORT).show()
            return@showEditor false
        }
        reloadNetwork()
        refreshValues()
        true
    }

    private fun showNetworkInterfacesPicker() {
        val interfaces = NetworkInterfaceCatalog.list()
        if (interfaces.isEmpty()) {
            Toast.makeText(this, R.string.settings_network_interfaces_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val initialSelection = NetworkInterfaceCatalog.resolveSelection(
            interfaces,
            settings.networkInterfaceSelection(),
            NetworkInterfaceCatalog.defaultSelection(this, interfaces)
        )
        val selected = initialSelection.toMutableSet()
        val content = layoutInflater.inflate(R.layout.dialog_network_interfaces, null)
        val list = content.findViewById<LinearLayout>(R.id.network_interfaces_list)
        interfaces.forEachIndexed { index, networkInterface ->
            val row = layoutInflater.inflate(R.layout.item_network_interface, list, false)
            val checkbox = row.findViewById<CheckBox>(R.id.network_interface_checkbox)
            row.findViewById<TextView>(R.id.network_interface_name).text =
                "[#${index + 1}] ${networkInterface.name}"
            row.findViewById<TextView>(R.id.network_interface_addresses).text =
                networkInterface.addresses.joinToString("\n")
            checkbox.isChecked = networkInterface.name in selected
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selected += networkInterface.name else selected -= networkInterface.name
            }
            row.setOnClickListener { checkbox.isChecked = !checkbox.isChecked }
            list.addView(row)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings_network_interfaces)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (selected.isEmpty()) {
                    Toast.makeText(this, R.string.settings_network_interfaces_select_one, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                settings.setNetworkInterfaceSelection(selected)
                refreshValues()
                reloadNetwork()
                dialog.dismiss()
            }
        }
        dialog.show()
        ThemeColors.apply(dialog)
    }

    private fun showEditor(
        title: Int,
        value: String,
        inputType: Int,
        lengthFilter: InputFilter? = null,
        onSave: (String) -> Boolean
    ) {
        val content = layoutInflater.inflate(R.layout.dialog_edit_device_name, null)
        val input = content.findViewById<EditText>(R.id.device_name_editor).apply {
            setText(value)
            setSelection(length())
            this.inputType = inputType
            if (lengthFilter != null) filters = arrayOf(lengthFilter)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .show()
        ThemeColors.apply(dialog)
        val customPanelId = resources.getIdentifier("customPanel", "id", "android")
        if (customPanelId != 0) dialog.findViewById<android.view.View>(customPanelId)?.minimumHeight = 0
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (onSave(input.text.toString().trim())) dialog.dismiss()
        }
    }

    private fun refreshValues() {
        deviceInfoValue.text = getString(
            R.string.settings_device_information_summary,
            settings.deviceName(),
            deviceTypeLabel(settings.deviceType()),
            settings.deviceModel()
        ).replace("\n", getString(R.string.settings_device_information_separator))
        languageValue.text = when (settings.language()) {
            AppLocale.CHINESE -> getString(R.string.language_chinese)
            AppLocale.ENGLISH -> getString(R.string.language_english)
            else -> getString(R.string.language_system)
        }
        themeColorValue.text = getString(ThemeColorPreset.fromId(settings.themeColor()).labelRes)
        portValue.text = settings.port().toString()
        multicastAddressValue.text = settings.multicastAddress()
        networkInterfacesValue.text = networkInterfaceSummary()
        receiveDirectoryValue.text = settings.receiveDirectoryName() ?: getString(R.string.settings_default_receive_directory)
    }

    private fun networkInterfaceSummary(): String {
        val interfaces = NetworkInterfaceCatalog.list()
        val selected = NetworkInterfaceCatalog.resolveSelection(
            interfaces,
            settings.networkInterfaceSelection(),
            NetworkInterfaceCatalog.defaultSelection(this, interfaces)
        )
        return interfaces.filter { it.name in selected }
            .joinToString("\n") { it.name }
            .ifBlank { getString(R.string.settings_network_interfaces_none) }
    }

    private fun deviceTypeLabel(value: String): String = when (DeviceType.fromValue(value)) {
        DeviceType.MOBILE -> getString(R.string.device_type_mobile)
        DeviceType.DESKTOP -> getString(R.string.device_type_desktop)
        DeviceType.WEB -> getString(R.string.device_type_web)
        DeviceType.HEADLESS -> getString(R.string.device_type_headless)
        DeviceType.SERVER -> getString(R.string.device_type_server)
    }

    private fun chooseReceiveDirectory() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, RECEIVE_DIRECTORY_REQUEST)
    }

    @Deprecated("Legacy Activity result API supports Android 5.0")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != RECEIVE_DIRECTORY_REQUEST || resultCode != RESULT_OK) return
        val resultData = data ?: return
        val uri = resultData.data ?: return
        try {
            @Suppress("WrongConstant")
            contentResolver.takePersistableUriPermission(
                uri,
                resultData.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            )
            settings.saveReceiveDirectory(uri, directoryName(uri))
            refreshValues()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.settings_receive_directory_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun directoryName(uri: Uri): String = try {
        DocumentsContract.getTreeDocumentId(uri).substringAfterLast('/').ifBlank {
            getString(R.string.settings_selected_receive_directory)
        }
    } catch (_: Exception) {
        getString(R.string.settings_selected_receive_directory)
    }

    private fun reloadNetwork() {
        startService(Intent(this, TransferService::class.java).setAction(TransferService.ACTION_RELOAD_SETTINGS))
    }

    private fun openDocument(title: Int, assetName: String) {
        startActivity(TextDocumentActivity.intent(this, getString(title), assetName))
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.open_external_link_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val RECEIVE_DIRECTORY_REQUEST = 1002
        const val REPOSITORY_URL = "https://github.com/mouse233/localsend-kotlin"
        const val RELEASES_URL = "$REPOSITORY_URL/releases"
        const val ISSUES_URL = "$REPOSITORY_URL/issues"
        const val LICENSE_FILE = "LICENSE"
        const val NOTICE_FILE = "NOTICE"
    }
}
