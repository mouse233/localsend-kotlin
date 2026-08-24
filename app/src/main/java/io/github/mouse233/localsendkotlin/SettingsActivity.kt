package io.github.mouse233.localsendkotlin

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.Switch
import io.github.mouse233.localsendkotlin.settings.AppSettings

class SettingsActivity : Activity() {
    private lateinit var settings: AppSettings
    private lateinit var deviceNameValue: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        settings = AppSettings(this)
        deviceNameValue = findViewById(R.id.device_name_value)
        refreshDeviceName()
        findViewById<android.view.View>(R.id.settings_back_button).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.device_name_row).setOnClickListener { showDeviceNameEditor() }
        findViewById<Switch>(R.id.checksum_switch).apply {
            isChecked = settings.createChecksums()
            setOnCheckedChangeListener { _, checked -> settings.setCreateChecksums(checked) }
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
                refreshDeviceName()
            }
            .show()
    }

    private fun refreshDeviceName() { deviceNameValue.text = settings.deviceName() }

    private fun showAbout() = AlertDialog.Builder(this)
        .setTitle(R.string.about_title)
        .setMessage(R.string.about_message)
        .setPositiveButton(android.R.string.ok, null)
        .show()
}
