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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.mouse233.localsendkotlin.model.ReceivedFile
import io.github.mouse233.localsendkotlin.model.RemoteDevice
import io.github.mouse233.localsendkotlin.model.ActiveTransferFile
import io.github.mouse233.localsendkotlin.transfer.IncomingTransferManager
import io.github.mouse233.localsendkotlin.transfer.TransferService
import io.github.mouse233.localsendkotlin.ui.DeviceAdapter
import io.github.mouse233.localsendkotlin.ui.ActiveTransferAdapter

class MainActivity : Activity(), TransferService.Listener {
    private lateinit var statusText: TextView
    private lateinit var transferProgress: ProgressBar
    private lateinit var cancelTransferButton: android.widget.Button
    private lateinit var activeTransferList: RecyclerView
    private var selectedFiles: List<Uri> = emptyList()
    private var transferService: TransferService? = null
    private var bound = false
    private val deviceAdapter = DeviceAdapter(::sendToDevice)
    private val activeTransferFiles = LinkedHashMap<String, ActiveTransferFile>()
    private val activeTransferAdapter = ActiveTransferAdapter { sessionId, fileId -> transferService?.cancelIncomingFile(sessionId, fileId) }

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
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.discovery_status)
        transferProgress = findViewById(R.id.transfer_progress)
        cancelTransferButton = findViewById(R.id.cancel_transfer_button)
        activeTransferList = findViewById(R.id.active_transfer_list)
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
        activeTransferList.apply { layoutManager = LinearLayoutManager(this@MainActivity); adapter = activeTransferAdapter }
        findViewById<android.view.View>(R.id.refresh_button).setOnClickListener { transferService?.refreshDevices() }
        findViewById<android.view.View>(R.id.select_file_button).setOnClickListener { chooseFile() }
        onDevicesChanged(emptyList())
        requestLegacyStoragePermission()
        requestNotificationPermission()
        startTransferService()
    }

    override fun onStart() {
        super.onStart()
        if (!bound) { bindService(Intent(this, TransferService::class.java), connection, BIND_AUTO_CREATE); bound = true }
    }

    override fun onStop() {
        transferService?.removeListener(this)
        if (bound) { unbindService(connection); bound = false }
        super.onStop()
    }

    @Deprecated("Legacy Activity result API supports Android 5.0")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
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

    private fun sendToDevice(device: RemoteDevice) {
        if (selectedFiles.isEmpty()) { Toast.makeText(this, R.string.select_file_first, Toast.LENGTH_SHORT).show(); return }
        transferService?.send(selectedFiles, device) ?: Toast.makeText(this, R.string.service_starting, Toast.LENGTH_SHORT).show()
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

    override fun onIncomingTransferRequest(request: IncomingTransferManager.PrepareUploadRequest, decide: (Boolean) -> Unit) {
        if (isFinishing || isDestroyed) { decide(false); return }
        val files = request.files.values.joinToString("\n") { "${it.fileName} (${formatBytes(it.size)})" }
        AlertDialog.Builder(this).setTitle(getString(R.string.incoming_request_title, request.info.alias)).setMessage(files)
            .setNegativeButton(R.string.incoming_request_reject) { _, _ -> decide(false) }
            .setPositiveButton(R.string.incoming_request_accept) { _, _ -> decide(true) }
            .setOnCancelListener { decide(false) }.show()
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

    private fun refreshActiveTransfers() {
        activeTransferAdapter.submitFiles(activeTransferFiles.values.toList())
        activeTransferList.visibility = if (activeTransferFiles.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
    }
    @TargetApi(Build.VERSION_CODES.M)
    private fun requestLegacyStoragePermission() {
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.M..Build.VERSION_CODES.P && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), LEGACY_STORAGE_PERMISSION_REQUEST)
    }
    private fun formatBytes(bytes: Long): String = when { bytes < 1024 -> "$bytes B"; bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0); bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0)); else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0)) }

    private companion object { const val FILE_REQUEST = 1001; const val LEGACY_STORAGE_PERMISSION_REQUEST = 1002; const val NOTIFICATION_PERMISSION_REQUEST = 1003 }
}
