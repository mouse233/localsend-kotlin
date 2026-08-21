package io.github.mouse233.localsendkotlin

import android.app.Activity
import android.app.AlertDialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import android.widget.ProgressBar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.mouse233.localsendkotlin.discovery.DiscoveryListener
import io.github.mouse233.localsendkotlin.discovery.DiscoveryManager
import io.github.mouse233.localsendkotlin.discovery.LocalIdentity
import io.github.mouse233.localsendkotlin.model.RemoteDevice
import io.github.mouse233.localsendkotlin.model.ReceivedFile
import io.github.mouse233.localsendkotlin.transfer.IncomingFileStore
import io.github.mouse233.localsendkotlin.transfer.IncomingTransferManager
import io.github.mouse233.localsendkotlin.transfer.UploadClient
import io.github.mouse233.localsendkotlin.ui.DeviceAdapter
import io.github.mouse233.localsendkotlin.ui.ReceivedFileAdapter

class MainActivity : Activity(), DiscoveryListener {
    private lateinit var statusText: TextView
    private lateinit var discoveryManager: DiscoveryManager
    private lateinit var uploadClient: UploadClient
    private lateinit var transferProgress: ProgressBar
    private lateinit var cancelUploadButton: android.widget.Button
    private var selectedFile: Uri? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val deviceAdapter = DeviceAdapter(::sendToDevice)
    private val receivedFileAdapter = ReceivedFileAdapter(::openReceivedFile)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.discovery_status)
        transferProgress = findViewById(R.id.transfer_progress)
        cancelUploadButton = findViewById(R.id.cancel_transfer_button)
        cancelUploadButton.setOnClickListener {
            uploadClient.cancelCurrent()
            discoveryManager.cancelIncomingTransfer()
            cancelUploadButton.visibility = android.view.View.GONE
            statusText.text = getString(R.string.upload_cancelled)
        }
        findViewById<RecyclerView>(R.id.device_list).apply { layoutManager = LinearLayoutManager(this@MainActivity); adapter = deviceAdapter }
        findViewById<RecyclerView>(R.id.received_file_list).apply { layoutManager = LinearLayoutManager(this@MainActivity); adapter = receivedFileAdapter }
        discoveryManager = DiscoveryManager(this, this)
        uploadClient = UploadClient(this, LocalIdentity(this))
        findViewById<android.view.View>(R.id.refresh_button).setOnClickListener { discoveryManager.announce() }
        findViewById<android.view.View>(R.id.select_file_button).setOnClickListener { chooseFile() }
        onDevicesChanged(emptyList())
        requestLegacyStoragePermission()
        Thread {
            val files = IncomingFileStore(this).listReceivedFiles()
            mainHandler.post { receivedFileAdapter.submitFiles(files) }
        }.start()
    }

    override fun onStart() { super.onStart(); discoveryManager.start() }
    override fun onStop() { discoveryManager.stop(); super.onStop() }

    @Deprecated("Legacy Activity result API supports Android 5.0")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_REQUEST && resultCode == RESULT_OK) {
            selectedFile = data?.data
            selectedFile?.let { statusText.text = getString(R.string.file_selected, it.lastPathSegment ?: "文件") }
        }
    }

    private fun chooseFile() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, FILE_REQUEST)
    private fun sendToDevice(device: RemoteDevice) {
        val uri = selectedFile ?: run { Toast.makeText(this, R.string.select_file_first, Toast.LENGTH_SHORT).show(); return }
        uploadClient.send(uri, device, object : UploadClient.Listener {
            override fun onStatus(message: String) = showStatus(message)
            override fun onProgress(sent: Long, total: Long) {
                val percent = if (total > 0) ((sent * 100L) / total).toInt().coerceIn(0, 100) else 0
                mainHandler.post {
                    transferProgress.visibility = android.view.View.VISIBLE
                    transferProgress.progress = percent
                    cancelUploadButton.visibility = android.view.View.VISIBLE
                    statusText.text = getString(R.string.upload_progress, percent)
                }
            }
            override fun onCompleted(name: String) {
                mainHandler.post {
                    transferProgress.progress = 100
                    transferProgress.visibility = android.view.View.GONE
                    cancelUploadButton.visibility = android.view.View.GONE
                    statusText.text = getString(R.string.upload_completed, name)
                }
            }
            override fun onError(message: String) {
                mainHandler.post {
                    transferProgress.visibility = android.view.View.GONE
                    cancelUploadButton.visibility = android.view.View.GONE
                    statusText.text = message
                }
            }
        })
    }
    private fun showStatus(message: String) { mainHandler.post { statusText.text = message } }
    override fun onDevicesChanged(devices: List<RemoteDevice>) { deviceAdapter.submitDevices(devices); if (selectedFile == null) statusText.text = if (devices.isEmpty()) getString(R.string.discovery_scanning) else resources.getQuantityString(R.plurals.device_count, devices.size, devices.size) }
    override fun onDiscoveryError(message: String) = showStatus(getString(R.string.discovery_error, message))
    override fun onIncomingTransferRequest(
        request: IncomingTransferManager.PrepareUploadRequest,
        decide: (Boolean) -> Unit
    ) {
        if (isFinishing || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed)) {
            decide(false)
            return
        }
        val files = request.files.values.joinToString("\n") { file ->
            "${file.fileName} (${formatBytes(file.size)})"
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.incoming_request_title, request.info.alias))
            .setMessage(files)
            .setNegativeButton(R.string.incoming_request_reject) { _, _ -> decide(false) }
            .setPositiveButton(R.string.incoming_request_accept) { _, _ -> decide(true) }
            .setOnCancelListener { decide(false) }
            .show()
    }

    override fun onFileReceiveProgress(fileName: String, received: Long, total: Long) {
        val percent = if (total > 0) ((received * 100L) / total).toInt().coerceIn(0, 100) else 0
        transferProgress.visibility = android.view.View.VISIBLE
        transferProgress.progress = percent
        cancelUploadButton.visibility = android.view.View.VISIBLE
        showStatus(getString(R.string.download_progress, fileName, percent))
    }

    override fun onFileReceiveCancelled(fileName: String) {
        transferProgress.visibility = android.view.View.GONE
        cancelUploadButton.visibility = android.view.View.GONE
        showStatus(getString(R.string.download_cancelled, fileName))
        mainHandler.post { Toast.makeText(this, R.string.download_cancelled_toast, Toast.LENGTH_SHORT).show() }
    }

    override fun onFileReceived(file: ReceivedFile) {
        val message = getString(R.string.download_completed, file.displayName)
        transferProgress.progress = 100
        transferProgress.visibility = android.view.View.GONE
        cancelUploadButton.visibility = android.view.View.GONE
        showStatus(message)
        mainHandler.post {
            receivedFileAdapter.addFile(file)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun openReceivedFile(file: ReceivedFile) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(file.uri, file.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            Toast.makeText(this, R.string.open_file_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestLegacyStoragePermission() {
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.M..Build.VERSION_CODES.P &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), LEGACY_STORAGE_PERMISSION_REQUEST)
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
        if (bytes < 1024 * 1024 * 1024) return "%.1f MB".format(bytes / (1024.0 * 1024.0))
        return "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }

    private companion object {
        const val FILE_REQUEST = 1001
        const val LEGACY_STORAGE_PERMISSION_REQUEST = 1002
    }
}
