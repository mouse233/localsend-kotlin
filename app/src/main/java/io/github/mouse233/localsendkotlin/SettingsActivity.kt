package io.github.mouse233.localsendkotlin

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import io.github.mouse233.localsendkotlin.settings.AppSettings
import io.github.mouse233.localsendkotlin.transfer.TransferService
import io.github.mouse233.localsendkotlin.ui.SystemBars

class SettingsActivity : Activity() {
    private lateinit var settings: AppSettings
    private lateinit var deviceNameValue: TextView
    private lateinit var portValue: TextView
    private lateinit var multicastAddressValue: TextView
    private lateinit var receiveDirectoryValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SystemBars.apply(this)
        setContentView(R.layout.activity_settings)
        settings = AppSettings(this)
        deviceNameValue = findViewById(R.id.device_name_value)
        portValue = findViewById(R.id.port_value)
        multicastAddressValue = findViewById(R.id.multicast_address_value)
        receiveDirectoryValue = findViewById(R.id.receive_directory_value)
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
        findViewById<android.view.View>(R.id.changelog_row).setOnClickListener {
            startActivity(Intent(this, ChangelogActivity::class.java))
        }
        findViewById<android.view.View>(R.id.about_settings_row).setOnClickListener { showAbout() }
    }

    private fun showDeviceNameEditor() {
        val content = layoutInflater.inflate(R.layout.dialog_edit_device_name, null)
        val input = content.findViewById<EditText>(R.id.device_name_editor).apply {
            setText(settings.deviceName())
            setSelection(length())
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
            setSelection(length())
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
        receiveDirectoryValue.text = settings.receiveDirectoryName() ?: getString(R.string.settings_default_receive_directory)
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
        val grantFlags = resultData.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            contentResolver.takePersistableUriPermission(uri, grantFlags)
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

    private fun showAbout() = AlertDialog.Builder(this)
        .setTitle(R.string.about_title)
        .setMessage(R.string.about_message)
        .setPositiveButton(android.R.string.ok, null)
        .show()

    private companion object {
        const val RECEIVE_DIRECTORY_REQUEST = 1002
    }
}
