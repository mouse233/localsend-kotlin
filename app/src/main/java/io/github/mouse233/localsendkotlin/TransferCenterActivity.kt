package io.github.mouse233.localsendkotlin

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.mouse233.localsendkotlin.model.ActiveTransferFile
import io.github.mouse233.localsendkotlin.model.ReceivedFile
import io.github.mouse233.localsendkotlin.model.RemoteDevice
import io.github.mouse233.localsendkotlin.transfer.IncomingReceiveOptions
import io.github.mouse233.localsendkotlin.transfer.IncomingTransferManager
import io.github.mouse233.localsendkotlin.transfer.TransferService
import io.github.mouse233.localsendkotlin.settings.AppSettings
import io.github.mouse233.localsendkotlin.ui.ActiveTransferAdapter
import io.github.mouse233.localsendkotlin.ui.SystemBars
import io.github.mouse233.localsendkotlin.ui.ThemeColors

/** Displays active incoming and outgoing sessions independently of the home screen. */
class TransferCenterActivity : LocalizedActivity(), TransferService.Listener {
    private lateinit var empty: TextView
    private val files = LinkedHashMap<String, ActiveTransferFile>()
    private val adapter = ActiveTransferAdapter(
        onCancelFile = { sessionId, fileId -> transferService?.cancelIncomingFile(sessionId, fileId) },
        onCancelSession = { confirmCancelSession() }
    )
    private var transferService: TransferService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            transferService = (binder as TransferService.LocalBinder).service()
            transferService?.addListener(this@TransferCenterActivity)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            transferService = null
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SystemBars.apply(this)
        setContentView(R.layout.activity_transfer_center)
        ThemeColors.apply(this)
        empty = findViewById(R.id.transfer_center_empty)
        findViewById<android.view.View>(R.id.transfer_center_back_button).setOnClickListener { finish() }
        findViewById<RecyclerView>(R.id.transfer_center_list).apply {
            layoutManager = LinearLayoutManager(this@TransferCenterActivity)
            adapter = this@TransferCenterActivity.adapter
            itemAnimator = null
        }
        refresh()
    }

    override fun onStart() {
        super.onStart()
        SystemBars.apply(this)
        ThemeColors.apply(this)
        if (!bound) {
            bindService(Intent(this, TransferService::class.java), connection, BIND_AUTO_CREATE)
            bound = true
        }
    }

    override fun onStop() {
        transferService?.removeListener(this)
        if (bound) {
            unbindService(connection)
            bound = false
        }
        super.onStop()
    }

    override fun onDevicesChanged(devices: List<RemoteDevice>) = Unit
    override fun onDiscoveryError(message: String) = Unit

    override fun onIncomingTransferRequest(
        request: IncomingTransferManager.PrepareUploadRequest,
        decide: (IncomingReceiveOptions?) -> Unit
    ) {
        val message = request.messageText()
        val content = if (message != null) message else request.files.values.joinToString("\n") { "${it.fileName} (${formatBytes(it.size)})" }
        AlertDialog.Builder(this)
            .setTitle(getString(if (message == null) R.string.incoming_request_title else R.string.incoming_message_title, request.info.alias))
            .setMessage(content)
            .setNegativeButton(R.string.incoming_request_reject) { _, _ -> decide(null) }
            .setPositiveButton(if (message == null) R.string.incoming_request_accept else R.string.close) { _, _ ->
                decide(IncomingReceiveOptions.forAll(request, AppSettings(this)))
            }
            .setOnCancelListener { decide(null) }
            .create()
            .also { it.show(); ThemeColors.apply(it) }
    }

    override fun onIncomingSessionPrepared(sessionId: String, request: IncomingTransferManager.PrepareUploadRequest) {
        request.files.forEach { (fileId, file) ->
            put(ActiveTransferFile(sessionId, fileId, file.fileName, 0L, file.size, ActiveTransferFile.Status.WAITING))
        }
    }

    override fun onOutgoingSessionPreparing(sessionId: String, files: List<ActiveTransferFile>) {
        files.forEach(::put)
    }

    override fun onOutgoingChecksumProgress(sessionId: String, current: Int, total: Int) {
        adapter.setChecksumProgress(sessionId, current, total)
    }

    override fun onOutgoingSessionPrepared(sessionId: String, files: List<ActiveTransferFile>) {
        files.forEach(::put)
    }

    override fun onOutgoingSessionStarted(preparationSessionId: String, sessionId: String, files: List<ActiveTransferFile>) {
        adapter.clearChecksumProgress(preparationSessionId)
        this.files.entries.removeAll { it.value.sessionId == preparationSessionId }
        files.forEach(::put)
        refresh()
    }

    override fun onActiveTransfersRestored(files: List<ActiveTransferFile>) {
        this.files.clear()
        files.forEach(::put)
        refresh()
    }

    override fun onFileReceiveProgress(file: ActiveTransferFile) {
        put(file)
    }

    override fun onFileSendProgress(file: ActiveTransferFile) {
        put(file)
    }

    override fun onFileReceiveCancelled(file: ActiveTransferFile, sessionComplete: Boolean) {
        put(file)
    }

    override fun onFileReceived(sessionId: String, fileId: String, file: ReceivedFile, sessionComplete: Boolean) {
        this.files["$sessionId:$fileId"]?.let {
            put(it.copy(receivedBytes = it.totalBytes, status = ActiveTransferFile.Status.COMPLETED))
        }
    }

    override fun onIncomingSessionCompleted(sessionId: String) = refresh()
    override fun onOutgoingSessionCompleted(sessionId: String) {
        adapter.clearChecksumProgress(sessionId)
        refresh()
    }

    override fun onUploadStatus(message: String) = Unit

    override fun onPinRequired(device: RemoteDevice, attempt: Int, reply: (String?) -> Unit) {
        val input = EditText(this).apply {
            hint = getString(R.string.pin_input_hint)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        var replied = false
        fun respond(value: String?) {
            if (replied) return
            replied = true
            reply(value)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.pin_required_title)
            .setMessage(getString(R.string.pin_required_message, device.alias, attempt))
            .setView(input)
            .setNegativeButton(android.R.string.cancel) { _, _ -> respond(null) }
            .setPositiveButton(R.string.submit, null)
            .setOnCancelListener { respond(null) }
            .create()
        dialog.show()
        ThemeColors.apply(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val pin = input.text.toString().trim()
            if (pin.isEmpty()) input.error = getString(R.string.invalid_pin) else {
                dialog.dismiss()
                respond(pin)
            }
        }
    }

    override fun onUploadProgress(fileName: String, fileIndex: Int, fileCount: Int, sent: Long, total: Long, totalSent: Long, totalBytes: Long) {
    }

    override fun onTransferStateRestored(title: String, percent: Int) { refresh() }
    override fun onTransferFinished(message: String) { refresh() }
    override fun onUploadCompleted(names: List<String>) { refresh() }
    override fun onUploadError(message: String) { refresh() }

    private fun put(file: ActiveTransferFile) {
        files["${file.sessionId}:${file.fileId}"] = file
        refresh()
    }

    private fun refresh() {
        adapter.submitFiles(files.values.toList())
        val hasFiles = files.isNotEmpty()
        empty.visibility = if (hasFiles) android.view.View.GONE else android.view.View.VISIBLE
        findViewById<RecyclerView>(R.id.transfer_center_list).visibility = if (hasFiles) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun confirmCancelSession() {
        if (files.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.cancel_all_transfers_title)
            .setMessage(R.string.cancel_all_transfers_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.cancel_transfer) { _, _ -> transferService?.cancelCurrent() }
            .create()
            .also { it.show(); ThemeColors.apply(it) }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
