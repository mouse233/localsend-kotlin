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
import io.github.mouse233.localsendkotlin.transfer.IncomingFileStore
import io.github.mouse233.localsendkotlin.transfer.IncomingTransferManager
import io.github.mouse233.localsendkotlin.transfer.TransferService
import io.github.mouse233.localsendkotlin.ui.DeviceAdapter
import io.github.mouse233.localsendkotlin.ui.ReceivedFileAdapter

class MainActivity : Activity(), TransferService.Listener {
    private lateinit var statusText: TextView
    private lateinit var transferProgress: ProgressBar
    private lateinit var cancelTransferButton: android.widget.Button
    private var selectedFile: Uri? = null
    private var transferService: TransferService? = null
    private var bound = false
    private val deviceAdapter = DeviceAdapter(::sendToDevice)
    private val receivedFileAdapter = ReceivedFileAdapter(::openReceivedFile)

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
        findViewById<android.view.View>(R.id.about_button).setOnClickListener { showAbout() }
        cancelTransferButton.setOnClickListener {
            transferService?.cancelCurrent()
            cancelTransferButton.visibility = android.view.View.GONE
            statusText.text = getString(R.string.upload_cancelled)
        }
        findViewById<RecyclerView>(R.id.device_list).apply { layoutManager = LinearLayoutManager(this@MainActivity); adapter = deviceAdapter }
        findViewById<RecyclerView>(R.id.received_file_list).apply { layoutManager = LinearLayoutManager(this@MainActivity); adapter = receivedFileAdapter }
        findViewById<android.view.View>(R.id.refresh_button).setOnClickListener { transferService?.announce() }
        findViewById<android.view.View>(R.id.select_file_button).setOnClickListener { chooseFile() }
        onDevicesChanged(emptyList())
        requestLegacyStoragePermission()
        requestNotificationPermission()
        startTransferService()
        Thread {
            val files = IncomingFileStore(this).listReceivedFiles()
            runOnUiThread { receivedFileAdapter.submitFiles(files) }
        }.start()
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
            selectedFile = data?.data
            selectedFile?.let { uri ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
                statusText.text = getString(R.string.file_selected, uri.lastPathSegment ?: "文件")
            }
        }
    }

    private fun chooseFile() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        type = "*/*"
    }, FILE_REQUEST)

    private fun sendToDevice(device: RemoteDevice) {
        val uri = selectedFile ?: run { Toast.makeText(this, R.string.select_file_first, Toast.LENGTH_SHORT).show(); return }
        transferService?.send(uri, device) ?: Toast.makeText(this, R.string.service_starting, Toast.LENGTH_SHORT).show()
    }

    private fun startTransferService() {
        val intent = Intent(this, TransferService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun showAbout() = AlertDialog.Builder(this).setTitle(R.string.about_title).setMessage(R.string.about_message).setPositiveButton(android.R.string.ok, null).show()

    override fun onDevicesChanged(devices: List<RemoteDevice>) {
        deviceAdapter.submitDevices(devices)
        if (selectedFile == null) statusText.text = if (devices.isEmpty()) getString(R.string.discovery_scanning) else resources.getQuantityString(R.plurals.device_count, devices.size, devices.size)
    }
    override fun onDiscoveryError(message: String) { statusText.text = getString(R.string.discovery_error, message) }
    override fun onUploadStatus(message: String) { statusText.text = message }
    override fun onTransferStateRestored(title: String, percent: Int) {
        transferProgress.visibility = android.view.View.VISIBLE
        transferProgress.progress = percent
        cancelTransferButton.visibility = android.view.View.VISIBLE
        statusText.text = title
    }
    override fun onUploadProgress(sent: Long, total: Long) {
        val percent = if (total > 0) ((sent * 100L) / total).toInt().coerceIn(0, 100) else 0
        transferProgress.visibility = android.view.View.VISIBLE; transferProgress.progress = percent; cancelTransferButton.visibility = android.view.View.VISIBLE
        statusText.text = getString(R.string.upload_progress, percent)
    }
    override fun onUploadCompleted(name: String) { transferProgress.visibility = android.view.View.GONE; cancelTransferButton.visibility = android.view.View.GONE; statusText.text = getString(R.string.upload_completed, name) }
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
    override fun onFileReceiveProgress(fileName: String, received: Long, total: Long) {
        val percent = if (total > 0) ((received * 100L) / total).toInt().coerceIn(0, 100) else 0
        transferProgress.visibility = android.view.View.VISIBLE; transferProgress.progress = percent; cancelTransferButton.visibility = android.view.View.VISIBLE
        statusText.text = getString(R.string.download_progress, fileName, percent)
    }
    override fun onFileReceiveCancelled(fileName: String) { transferProgress.visibility = android.view.View.GONE; cancelTransferButton.visibility = android.view.View.GONE; statusText.text = getString(R.string.download_cancelled, fileName) }
    override fun onFileReceived(file: ReceivedFile) {
        val message = getString(R.string.download_completed, file.displayName)
        transferProgress.visibility = android.view.View.GONE; cancelTransferButton.visibility = android.view.View.GONE; statusText.text = message
        receivedFileAdapter.addFile(file); Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun openReceivedFile(file: ReceivedFile) {
        try { startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(file.uri, file.mimeType); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }) }
        catch (_: Exception) { Toast.makeText(this, R.string.open_file_failed, Toast.LENGTH_SHORT).show() }
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
