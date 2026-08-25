package io.github.mouse233.localsendkotlin

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.InputType
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.mouse233.localsendkotlin.model.ReceivedFile
import io.github.mouse233.localsendkotlin.model.RemoteDevice
import io.github.mouse233.localsendkotlin.model.ActiveTransferFile
import io.github.mouse233.localsendkotlin.discovery.LocalNetworkAddress
import io.github.mouse233.localsendkotlin.discovery.ManualEndpoint
import io.github.mouse233.localsendkotlin.settings.AppSettings
import io.github.mouse233.localsendkotlin.settings.AppLocale
import io.github.mouse233.localsendkotlin.transfer.IncomingTransferManager
import io.github.mouse233.localsendkotlin.transfer.IncomingReceiveOptions
import io.github.mouse233.localsendkotlin.transfer.TransferService
import io.github.mouse233.localsendkotlin.ui.DeviceAdapter
import io.github.mouse233.localsendkotlin.ui.ActiveTransferAdapter
import io.github.mouse233.localsendkotlin.ui.SystemBars

class MainActivity : Activity(), TransferService.Listener {
    private lateinit var statusText: TextView
    private lateinit var transferProgress: ProgressBar
    private lateinit var cancelTransferButton: android.widget.Button
    private lateinit var activeTransferList: RecyclerView
    private lateinit var localEndpointDeviceName: TextView
    private lateinit var localEndpointBindLabel: TextView
    private lateinit var localEndpointAddresses: TextView
    private var selectedFiles: List<Uri> = emptyList()
    private var appliedLanguage: String? = null
    private var transferService: TransferService? = null
    private var bound = false
    private val deviceAdapter = DeviceAdapter(::sendToDevice, ::verifyDevice)
    private var pendingVerificationRequest: IncomingTransferManager.PrepareUploadRequest? = null
    private var pendingVerificationDecision: ((IncomingReceiveOptions?) -> Unit)? = null
    private var pendingReceiveSettingsRequest: IncomingTransferManager.PrepareUploadRequest? = null
    private var pendingReceiveSettingsDecision: ((IncomingReceiveOptions?) -> Unit)? = null
    private var pendingReceiveSettingsOptions: IncomingReceiveOptions? = null
    private val activeTransferFiles = LinkedHashMap<String, ActiveTransferFile>()
    private val activeTransferAdapter = ActiveTransferAdapter { sessionId, fileId ->
        transferService?.cancelIncomingFile(sessionId, fileId)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            transferService = (binder as TransferService.LocalBinder).service()
            transferService?.addListener(this@MainActivity)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            transferService = null
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SystemBars.apply(this)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.discovery_status)
        transferProgress = findViewById(R.id.transfer_progress)
        cancelTransferButton = findViewById(R.id.cancel_transfer_button)
        activeTransferList = findViewById(R.id.active_transfer_list)
        localEndpointDeviceName = findViewById(R.id.local_endpoint_device_name)
        localEndpointBindLabel = findViewById(R.id.local_endpoint_bind_label)
        localEndpointAddresses = findViewById(R.id.local_endpoint_addresses)
        appliedLanguage = AppSettings(this).language()
        findViewById<android.view.View>(R.id.history_button).setOnClickListener {
            startActivity(Intent(this, ReceiveHistoryActivity::class.java))
        }
        findViewById<android.view.View>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        cancelTransferButton.setOnClickListener {
            transferService?.cancelCurrent()
            cancelTransferButton.visibility = android.view.View.GONE
            statusText.text = getString(R.string.upload_cancelled)
        }
        findViewById<RecyclerView>(R.id.device_list).apply { layoutManager = LinearLayoutManager(this@MainActivity); adapter = deviceAdapter }
        activeTransferList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = activeTransferAdapter
            // Progress updates replace a queue item frequently. The default change
            // animation fades its controls, which makes the cancel button flicker.
            itemAnimator = null
        }
        findViewById<android.view.View>(R.id.refresh_button).setOnClickListener { transferService?.refreshDevices() }
        findViewById<android.view.View>(R.id.manual_send_button).setOnClickListener { showManualSendDialog() }
        findViewById<android.view.View>(R.id.select_file_button).setOnClickListener { chooseFile() }
        onDevicesChanged(emptyList())
        updateLocalEndpoint()
        requestLegacyStoragePermission()
        requestNotificationPermission()
        startTransferService()
    }

    override fun onStart() {
        super.onStart()
        if (!bound) { bindService(Intent(this, TransferService::class.java), connection, BIND_AUTO_CREATE); bound = true }
    }

    override fun onResume() {
        super.onResume()
        val language = AppSettings(this).language()
        if (language != appliedLanguage) {
            AppLocale.apply(this, language)
            appliedLanguage = language
            recreate()
            return
        }
        updateLocalEndpoint()
    }

    override fun onStop() {
        transferService?.removeListener(this)
        if (bound) { unbindService(connection); bound = false }
        super.onStop()
    }

    @Deprecated("Legacy Activity result API supports Android 5.0")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VERIFICATION_REQUEST) {
            val request = pendingVerificationRequest
            val decide = pendingVerificationDecision
            pendingVerificationRequest = null
            pendingVerificationDecision = null
            if (request != null && decide != null) showIncomingRequest(request, decide, pendingReceiveSettingsOptions)
            return
        }
        if (requestCode == RECEIVE_SETTINGS_REQUEST) {
            val request = pendingReceiveSettingsRequest
            val decide = pendingReceiveSettingsDecision
            pendingReceiveSettingsRequest = null
            pendingReceiveSettingsDecision = null
            if (request != null && decide != null && resultCode == RESULT_OK) {
                val selectedIds = data?.getStringArrayListExtra(ReceiveSettingsActivity.EXTRA_SELECTED_FILE_IDS).orEmpty().toSet()
                val allIds = data?.getStringArrayListExtra(ReceiveSettingsActivity.EXTRA_ALL_FILE_IDS).orEmpty()
                val allNames = data?.getStringArrayListExtra(ReceiveSettingsActivity.EXTRA_FILE_NAMES).orEmpty()
                val renamedFiles = allIds.mapIndexedNotNull { index, fileId ->
                    allNames.getOrNull(index)?.trim()?.takeIf { it.isNotBlank() }?.let { fileId to it }
                }.toMap()
                val directory = data?.getStringExtra(ReceiveSettingsActivity.EXTRA_DIRECTORY_URI)?.let(Uri::parse)
                pendingReceiveSettingsOptions = IncomingReceiveOptions(
                    selectedIds, renamedFiles, directory,
                    data?.getBooleanExtra(ReceiveSettingsActivity.EXTRA_SAVE_TO_GALLERY, false) == true
                )
            }
            if (request != null && decide != null) showIncomingRequest(request, decide, pendingReceiveSettingsOptions)
            return
        }
        if (requestCode == FILE_REQUEST && resultCode == RESULT_OK) {
            selectedFiles = buildList {
                data?.clipData?.let { clips -> for (index in 0 until clips.itemCount) add(clips.getItemAt(index).uri) }
                    ?: data?.data?.let(::add)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) selectedFiles.forEach { uri ->
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            }
            if (selectedFiles.isNotEmpty()) statusText.text = getString(R.string.files_selected, selectedFiles.size)
        }
    }

    private fun chooseFile() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        type = "*/*"
    }, FILE_REQUEST)

    private fun showManualSendDialog() {
        if (selectedFiles.isEmpty()) {
            Toast.makeText(this, R.string.select_file_first, Toast.LENGTH_SHORT).show()
            return
        }
        val content = layoutInflater.inflate(R.layout.dialog_manual_send, null)
        val input = content.findViewById<EditText>(R.id.manual_endpoint_editor).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setSelectAllOnFocus(true)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.manual_send_title)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.manual_send, null)
            .create()
        dialog.show()
        val customPanelId = resources.getIdentifier("customPanel", "id", "android")
        if (customPanelId != 0) dialog.findViewById<android.view.View>(customPanelId)?.minimumHeight = 0
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val endpoint = try {
                ManualEndpoint.parse(input.text.toString())
            } catch (_: IllegalArgumentException) {
                input.error = getString(R.string.manual_send_invalid_address)
                return@setOnClickListener
            }
            dialog.dismiss()
            statusText.text = getString(R.string.manual_send_connecting)
            transferService?.sendManual(selectedFiles, endpoint)
                ?: Toast.makeText(this, R.string.service_starting, Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendToDevice(device: RemoteDevice) {
        if (selectedFiles.isEmpty()) { Toast.makeText(this, R.string.select_file_first, Toast.LENGTH_SHORT).show(); return }
        transferService?.send(selectedFiles, device) ?: Toast.makeText(this, R.string.service_starting, Toast.LENGTH_SHORT).show()
    }

    private fun verifyDevice(device: RemoteDevice) {
        startActivity(Intent(this, VerificationActivity::class.java).putExtra(VerificationActivity.EXTRA_FINGERPRINT, device.fingerprint))
    }

    private fun startTransferService() {
        val intent = Intent(this, TransferService::class.java)
        // This is called while the Activity is visible. Starting normally avoids
        // Xiaomi Android 16 treating the foreground-service promotion as late.
        startService(intent)
    }

    override fun onDevicesChanged(devices: List<RemoteDevice>) {
        deviceAdapter.submitDevices(devices)
        if (selectedFiles.isEmpty()) statusText.text = if (devices.isEmpty()) getString(R.string.discovery_scanning) else resources.getQuantityString(R.plurals.device_count, devices.size, devices.size)
    }
    override fun onDiscoveryError(message: String) { statusText.text = getString(R.string.discovery_error, message) }
    override fun onIncomingSessionPrepared(sessionId: String, request: IncomingTransferManager.PrepareUploadRequest) {
        request.files.forEach { (fileId, file) ->
            activeTransferFiles["$sessionId:$fileId"] = ActiveTransferFile(sessionId, fileId, file.fileName, 0L, file.size, ActiveTransferFile.Status.WAITING)
        }
        refreshActiveTransfers()
    }
    override fun onOutgoingSessionPrepared(sessionId: String, files: List<ActiveTransferFile>) {
        files.forEach { file -> activeTransferFiles["${file.sessionId}:${file.fileId}"] = file }
        refreshActiveTransfers()
    }
    override fun onUploadStatus(message: String) { statusText.text = message }
    override fun onTransferStateRestored(title: String, percent: Int) {
        transferProgress.visibility = android.view.View.VISIBLE
        transferProgress.progress = percent
        cancelTransferButton.visibility = android.view.View.VISIBLE
        statusText.text = title
    }
    override fun onUploadProgress(fileName: String, fileIndex: Int, fileCount: Int, sent: Long, total: Long, totalSent: Long, totalBytes: Long) {
        val percent = if (totalBytes > 0) ((totalSent * 100L) / totalBytes).toInt().coerceIn(0, 100) else 0
        transferProgress.visibility = android.view.View.VISIBLE; transferProgress.progress = percent; cancelTransferButton.visibility = android.view.View.VISIBLE
        statusText.text = getString(R.string.upload_file_progress, fileIndex + 1, fileCount, fileName, percent)
    }
    override fun onUploadCompleted(names: List<String>) { transferProgress.visibility = android.view.View.GONE; cancelTransferButton.visibility = android.view.View.GONE; statusText.text = getString(R.string.upload_completed, names.joinToString("、")) }
    override fun onUploadError(message: String) { transferProgress.visibility = android.view.View.GONE; cancelTransferButton.visibility = android.view.View.GONE; statusText.text = message }
    override fun onTransferFinished(message: String) {
        transferProgress.progress = 100
        transferProgress.visibility = android.view.View.GONE
        cancelTransferButton.visibility = android.view.View.GONE
        statusText.text = message
    }

    override fun onIncomingTransferRequest(request: IncomingTransferManager.PrepareUploadRequest, decide: (IncomingReceiveOptions?) -> Unit) {
        if (isFinishing || isDestroyed) { decide(null); return }
        showIncomingRequest(request, decide, IncomingReceiveOptions.forAll(request, AppSettings(this)))
    }

    private fun showIncomingRequest(request: IncomingTransferManager.PrepareUploadRequest, decide: (IncomingReceiveOptions?) -> Unit, options: IncomingReceiveOptions?) {
        val content = layoutInflater.inflate(R.layout.dialog_incoming_request, null)
        content.findViewById<TextView>(R.id.incoming_file_list).text = request.files.values.joinToString("\n") { "${it.fileName} (${formatBytes(it.size)})" }
        val dialog = AlertDialog.Builder(this).setTitle(getString(R.string.incoming_request_title, request.info.alias)).setView(content)
            .setNegativeButton(R.string.incoming_request_reject) { _, _ -> decide(null) }
            .setNeutralButton(R.string.incoming_settings) { _, _ ->
                pendingReceiveSettingsRequest = request
                pendingReceiveSettingsDecision = decide
                pendingReceiveSettingsOptions = options
                startActivityForResult(ReceiveSettingsActivity.intent(this, request, options), RECEIVE_SETTINGS_REQUEST)
            }
            .setPositiveButton(R.string.incoming_request_accept) { _, _ -> decide(options) }
            .setOnCancelListener { decide(null) }.create()
        dialog.show()
        val settingsButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        val buttonPanel = settingsButton?.parent as? android.view.ViewGroup
        if (settingsButton != null && buttonPanel != null) {
            val verifyButton = android.widget.Button(this, null, android.R.attr.buttonBarNeutralButtonStyle).apply {
                setText(R.string.verification_title)
                isAllCaps = false
                setOnClickListener {
                    pendingVerificationRequest = request
                    pendingVerificationDecision = decide
                    pendingReceiveSettingsOptions = options
                    dialog.dismiss()
                    startActivityForResult(Intent(this@MainActivity, VerificationActivity::class.java).putExtra(VerificationActivity.EXTRA_FINGERPRINT, request.info.fingerprint), VERIFICATION_REQUEST)
                }
            }
            val settingsIndex = buttonPanel.indexOfChild(settingsButton)
            buttonPanel.addView(verifyButton, settingsIndex)
        }
    }
    override fun onFileReceiveProgress(file: ActiveTransferFile) {
        activeTransferFiles["${file.sessionId}:${file.fileId}"] = file
        refreshActiveTransfers()
        transferProgress.visibility = android.view.View.GONE
        cancelTransferButton.visibility = android.view.View.VISIBLE
        val totalReceived = activeTransferFiles.values.sumOf { it.receivedBytes }
        val totalBytes = activeTransferFiles.values.sumOf { it.totalBytes }
        val percent = if (totalBytes > 0L) ((totalReceived * 100L) / totalBytes).toInt().coerceIn(0, 100) else 0
        statusText.text = getString(R.string.receiving_files_progress, percent)
    }
    override fun onFileSendProgress(file: ActiveTransferFile) {
        activeTransferFiles["${file.sessionId}:${file.fileId}"] = file
        refreshActiveTransfers()
    }
    override fun onFileReceiveCancelled(file: ActiveTransferFile, sessionComplete: Boolean) {
        activeTransferFiles["${file.sessionId}:${file.fileId}"] = file
        refreshActiveTransfers()
        statusText.text = getString(R.string.download_cancelled, file.fileName)
        if (sessionComplete) cancelTransferButton.visibility = android.view.View.GONE
    }
    override fun onFileReceived(sessionId: String, fileId: String, file: ReceivedFile, sessionComplete: Boolean) {
        val key = "$sessionId:$fileId"
        activeTransferFiles[key]?.let { activeTransferFiles[key] = it.copy(receivedBytes = it.totalBytes, status = ActiveTransferFile.Status.COMPLETED) }
        refreshActiveTransfers()
        val message = getString(R.string.download_completed, file.displayName)
        statusText.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        if (sessionComplete) cancelTransferButton.visibility = android.view.View.GONE
    }
    override fun onIncomingSessionCompleted(sessionId: String) {
        activeTransferFiles.keys.filter { it.startsWith("$sessionId:") }.toList().forEach(activeTransferFiles::remove)
        refreshActiveTransfers()
    }
    override fun onOutgoingSessionCompleted(sessionId: String) {
        activeTransferFiles.keys.filter { it.startsWith("$sessionId:") }.toList().forEach(activeTransferFiles::remove)
        refreshActiveTransfers()
    }

    private fun refreshActiveTransfers() {
        activeTransferAdapter.submitFiles(activeTransferFiles.values.toList())
        activeTransferList.visibility = if (activeTransferFiles.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun updateLocalEndpoint() {
        val settings = AppSettings(this)
        localEndpointDeviceName.text = getString(R.string.local_endpoint_device_name, settings.deviceName())
        localEndpointBindLabel.text = getString(R.string.local_endpoint_bind_label)
        localEndpointAddresses.text = if (!settings.serverEnabled()) {
            getString(R.string.local_endpoint_server_disabled_value)
        } else {
            val allAddresses = LocalNetworkAddress.endpoints(this)
            val addresses = LocalNetworkAddress.visibleEndpoints(allAddresses, settings.hideIpv6BindAddresses())
            when {
                addresses.isNotEmpty() -> addresses.joinToString("\n") { endpoint ->
                    val address = endpoint.address.substringBefore('%')
                    val host = if (address.contains(':')) "[$address]" else address
                    "$host:${settings.port()}"
                }
                allAddresses.isEmpty() -> getString(R.string.local_endpoint_waiting_for_network_value, settings.port())
                else -> getString(R.string.local_endpoint_ipv6_hidden_value)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
    }
    @TargetApi(Build.VERSION_CODES.M)
    private fun requestLegacyStoragePermission() {
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.M..Build.VERSION_CODES.P && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), LEGACY_STORAGE_PERMISSION_REQUEST)
    }
    private fun formatBytes(bytes: Long): String = when { bytes < 1024 -> "$bytes B"; bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0); bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0)); else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0)) }

    private companion object { const val FILE_REQUEST = 1001; const val LEGACY_STORAGE_PERMISSION_REQUEST = 1002; const val NOTIFICATION_PERMISSION_REQUEST = 1003; const val VERIFICATION_REQUEST = 1004; const val RECEIVE_SETTINGS_REQUEST = 1005 }
}
