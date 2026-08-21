package io.github.mouse233.localsendkotlin

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.mouse233.localsendkotlin.discovery.DiscoveryListener
import io.github.mouse233.localsendkotlin.discovery.DiscoveryManager
import io.github.mouse233.localsendkotlin.discovery.LocalIdentity
import io.github.mouse233.localsendkotlin.model.RemoteDevice
import io.github.mouse233.localsendkotlin.transfer.UploadClient
import io.github.mouse233.localsendkotlin.ui.DeviceAdapter

class MainActivity : Activity(), DiscoveryListener {
    private lateinit var statusText: TextView
    private lateinit var discoveryManager: DiscoveryManager
    private lateinit var uploadClient: UploadClient
    private var selectedFile: Uri? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val deviceAdapter = DeviceAdapter(::sendToDevice)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.discovery_status)
        findViewById<RecyclerView>(R.id.device_list).apply { layoutManager = LinearLayoutManager(this@MainActivity); adapter = deviceAdapter }
        discoveryManager = DiscoveryManager(this, this)
        uploadClient = UploadClient(this, LocalIdentity(this))
        findViewById<android.view.View>(R.id.refresh_button).setOnClickListener { discoveryManager.announce() }
        findViewById<android.view.View>(R.id.select_file_button).setOnClickListener { chooseFile() }
        onDevicesChanged(emptyList())
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
            override fun onProgress(sent: Long, total: Long) = showStatus(getString(R.string.upload_progress, (sent * 100 / total).toInt()))
            override fun onCompleted(name: String) = showStatus(getString(R.string.upload_completed, name))
            override fun onError(message: String) = showStatus(message)
        })
    }
    private fun showStatus(message: String) { mainHandler.post { statusText.text = message } }
    override fun onDevicesChanged(devices: List<RemoteDevice>) { deviceAdapter.submitDevices(devices); if (selectedFile == null) statusText.text = if (devices.isEmpty()) getString(R.string.discovery_scanning) else resources.getQuantityString(R.plurals.device_count, devices.size, devices.size) }
    override fun onDiscoveryError(message: String) = showStatus(getString(R.string.discovery_error, message))
    private companion object { const val FILE_REQUEST = 1001 }
}
