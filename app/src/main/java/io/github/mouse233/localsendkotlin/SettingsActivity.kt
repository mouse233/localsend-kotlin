package io.github.mouse233.localsendkotlin

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import io.github.mouse233.localsendkotlin.settings.AppSettings
import io.github.mouse233.localsendkotlin.transfer.TransferService

class SettingsActivity : Activity() {
    private lateinit var settings: AppSettings
    private lateinit var deviceNameValue: TextView
    private lateinit var portValue: TextView
    private lateinit var multicastAddressValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        settings = AppSettings(this)
        deviceNameValue = findViewById(R.id.device_name_value)
        portValue = findViewById(R.id.port_value)
        multicastAddressValue = findViewById(R.id.multicast_address_value)
        refreshValues()
        findViewById<android.view.View>(R.id.settings_back_button).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.device_name_row).setOnClickListener { showDeviceNameEditor() }
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
        val saveHistorySwitch = findViewById<Switch>(R.id.save_history_switch).apply {
            isChecked = settings.saveReceiveHistory()
            setOnCheckedChangeListener { _, checked -> settings.setSaveReceiveHistory(checked) }
        }
        findViewById<android.view.View>(R.id.save_history_row).setOnClickListener { saveHistorySwitch.toggle() }
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
        findViewById<android.view.View>(R.id.changelog_row).setOnClickListener {
            startActivity(Intent(this, ChangelogActivity::class.java))
        }
        findViewById<android.view.View>(R.id.about_settings_row).setOnClickListener { showAbout() }
    }

    private fun showDeviceNameEditor() {
        val content = layoutInflater.inflate(R.layout.dialog_edit_device_name, null)
        val input = content.findViewById<EditText>(R.id.device_name_editor).apply {
            setText(settings.deviceName())
            setSelectAllOnFocus(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(64))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_device_name)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                settings.saveDeviceName(input.text.toString())
                refreshValues()
            }
            .show()
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

    private fun showEditor(title: Int, value: String, inputType: Int, onSave: (String) -> Boolean) {
        val content = layoutInflater.inflate(R.layout.dialog_edit_device_name, null)
        val input = content.findViewById<EditText>(R.id.device_name_editor).apply {
            setText(value)
            setSelectAllOnFocus(true)
            this.inputType = inputType
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (onSave(input.text.toString().trim())) dialog.dismiss()
        }
    }

    private fun refreshValues() {
        deviceNameValue.text = settings.deviceName()
        portValue.text = settings.port().toString()
        multicastAddressValue.text = settings.multicastAddress()
    }

    private fun reloadNetwork() {
        startService(Intent(this, TransferService::class.java).setAction(TransferService.ACTION_RELOAD_SETTINGS))
    }

    private fun showAbout() = AlertDialog.Builder(this)
        .setTitle(R.string.about_title)
        .setMessage(R.string.about_message)
        .setPositiveButton(android.R.string.ok, null)
        .show()
}
