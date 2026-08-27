package io.github.mouse233.localsendkotlin

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.mouse233.localsendkotlin.history.ReceiveHistoryStore
import io.github.mouse233.localsendkotlin.model.ReceiveHistoryEntry
import io.github.mouse233.localsendkotlin.settings.AppSettings
import io.github.mouse233.localsendkotlin.transfer.IncomingMessageLink
import io.github.mouse233.localsendkotlin.ui.ReceiveHistoryAdapter
import io.github.mouse233.localsendkotlin.ui.SystemBars
import io.github.mouse233.localsendkotlin.ui.ThemeColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ReceiveHistoryActivity : Activity() {
    private lateinit var store: ReceiveHistoryStore
    private lateinit var settings: AppSettings
    private lateinit var adapter: ReceiveHistoryAdapter
    private lateinit var emptyView: TextView
    private val databaseExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SystemBars.apply(this)
        setContentView(R.layout.activity_receive_history)
        ThemeColors.apply(this)
        store = ReceiveHistoryStore(this)
        settings = AppSettings(this)
        emptyView = findViewById(R.id.receive_history_empty)
        adapter = ReceiveHistoryAdapter(::openFile, ::showDetails, ::deleteEntry)
        findViewById<RecyclerView>(R.id.receive_history_list).apply {
            layoutManager = LinearLayoutManager(this@ReceiveHistoryActivity)
            adapter = this@ReceiveHistoryActivity.adapter
        }
        findViewById<View>(R.id.history_back_button).setOnClickListener { finish() }
        findViewById<View>(R.id.open_directory_button).setOnClickListener { openDirectory() }
        findViewById<View>(R.id.clear_history_button).setOnClickListener { confirmClear() }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onDestroy() {
        if (::store.isInitialized) databaseExecutor.execute { store.close() }
        databaseExecutor.shutdown()
        super.onDestroy()
    }

    private fun reload() {
        databaseExecutor.execute {
            val entries = store.list()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                adapter.submitEntries(entries)
                emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun openFile(entry: ReceiveHistoryEntry) {
        if (entry.isMessage) {
            showMessage(entry)
            return
        }
        if (!fileExists(entry)) {
            Toast.makeText(this, R.string.file_no_longer_exists, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(entry.uri, entry.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            Toast.makeText(this, R.string.open_file_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMessage(entry: ReceiveHistoryEntry) {
        val message = entry.displayName
        val linkUri = IncomingMessageLink.detect(message)?.let(Uri::parse)
        val content = layoutInflater.inflate(R.layout.dialog_incoming_request, null)
        content.findViewById<TextView>(R.id.incoming_file_list).apply {
            text = message
            setTextIsSelectable(true)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.incoming_message_title, entry.senderAlias))
            .setView(content)
            .setNegativeButton(R.string.close, null)
            .setPositiveButton(R.string.content_action_clipboard) { _, _ ->
                copyMessage(message)
            }
            .create()
        dialog.show()
        ThemeColors.apply(dialog)
        if (linkUri != null) {
            val copyButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val buttonPanel = copyButton?.parent as? android.view.ViewGroup
            if (copyButton != null && buttonPanel != null) {
                val openButton = android.widget.Button(this, null, android.R.attr.buttonBarNeutralButtonStyle).apply {
                    setText(R.string.open_file)
                    isAllCaps = false
                    setOnClickListener {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, linkUri))
                            dialog.dismiss()
                        } catch (_: Exception) {
                            Toast.makeText(this@ReceiveHistoryActivity, R.string.open_external_link_failed, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                buttonPanel.addView(openButton, buttonPanel.indexOfChild(copyButton) + 1)
            }
        }
    }

    private fun copyMessage(message: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("LocalSend", message))
        Toast.makeText(this, R.string.content_action_copied, Toast.LENGTH_SHORT).show()
    }

    private fun openDirectory() {
        val selectedTree = settings.receiveDirectoryUri()
        val localDirectory = selectedTree?.let(::primaryExternalDirectory) ?: defaultDirectory()
        val localUri = localDirectory?.let(::fileProviderUri)
        val folderUri = localUri ?: selectedTree?.let { tree ->
            // SAF destinations are already content URIs. Convert the tree URI
            // to its document URI, matching LocalSend's Android implementation.
            DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        } ?: defaultDirectoryDocumentUri()
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            // open_filex uses */* for a plain filesystem directory path.
            // This broad type is what makes the system show all apps that can
            // accept the location, matching the official LocalSend behavior.
            val mimeType = if (localUri != null) "*/*" else DocumentsContract.Document.MIME_TYPE_DIR
            setDataAndType(folderUri, mimeType)
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            // LocalSend opens this generic ACTION_VIEW directly. Android then
            // resolves all file managers that support browsing directories.
            startActivity(viewIntent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, selectedTree ?: folderUri)
                    }
                })
            } catch (_: Exception) {
                Toast.makeText(this, R.string.open_directory_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun defaultDirectory(): File = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "LocalSend Kotlin"
        )

    private fun primaryExternalDirectory(treeUri: Uri): File? = try {
        val treeId = DocumentsContract.getTreeDocumentId(treeUri)
        if (treeId.startsWith("primary:")) {
            File(
                Environment.getExternalStorageDirectory(),
                treeId.removePrefix("primary:")
            )
        } else null
    } catch (_: Exception) {
        null
    }

    private fun fileProviderUri(directory: File): Uri? {
        return try {
            if (!directory.exists()) directory.mkdirs()
            FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", directory)
        } catch (_: Exception) {
            null
        }
    }

    private fun defaultDirectoryDocumentUri(): Uri = DocumentsContract.buildDocumentUri(
        EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY,
        "primary:Download/LocalSend Kotlin"
    )

    private fun fileExists(entry: ReceiveHistoryEntry): Boolean = try {
        contentResolver.openFileDescriptor(entry.uri, "r")?.use { true } ?: false
    } catch (_: Exception) {
        false
    }

    private fun showDetails(entry: ReceiveHistoryEntry) {
        val detailsView = layoutInflater.inflate(R.layout.dialog_receive_file_details, null)
        detailsView.findViewById<TextView>(R.id.file_info_name).text = entry.displayName
        detailsView.findViewById<TextView>(R.id.file_info_path).text = displayPath(entry)
        detailsView.findViewById<TextView>(R.id.file_info_size).text = formatBytes(entry.size)
        detailsView.findViewById<TextView>(R.id.file_info_sender).text = entry.senderAlias
        detailsView.findViewById<TextView>(R.id.file_info_time).text = DATE_FORMAT.format(Date(entry.receivedAt))
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.file_info_title)
            .setView(detailsView)
            .setPositiveButton(R.string.close, null)
            .show()
        ThemeColors.apply(dialog)
    }

    private fun confirmClear() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.clear_history_title)
            .setMessage(R.string.clear_history_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_history_records) { _, _ ->
                databaseExecutor.execute {
                    store.clear()
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) reload()
                    }
                }
            }
            .show()
        ThemeColors.apply(dialog)
    }

    private fun deleteEntry(entry: ReceiveHistoryEntry) {
        databaseExecutor.execute {
            store.delete(entry.id)
            runOnUiThread {
                if (!isFinishing && !isDestroyed) reload()
            }
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "%.1f KB".format(Locale.US, bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(Locale.US, bytes / (1024.0 * 1024.0))
        else -> "%.1f GB".format(Locale.US, bytes / (1024.0 * 1024.0 * 1024.0))
    }

    @Suppress("DEPRECATION")
    private fun displayPath(entry: ReceiveHistoryEntry): String {
        if (DocumentsContract.isDocumentUri(this, entry.uri)) {
            val documentId = DocumentsContract.getDocumentId(entry.uri)
            if (documentId.startsWith("primary:")) {
                return "${Environment.getExternalStorageDirectory().absolutePath}/${documentId.removePrefix("primary:")}"
            }
            return documentId
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentResolver.query(
                entry.uri,
                arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val relativePath = cursor.getString(0)
                    val displayName = cursor.getString(1) ?: entry.displayName
                    if (!relativePath.isNullOrBlank()) {
                        return "/storage/emulated/0/$relativePath$displayName"
                    }
                }
            }
        }
        return entry.uri.toString()
    }

    private companion object {
        val DATE_FORMAT = SimpleDateFormat("yyyy/M/d HH:mm", Locale.CHINA)
        const val EXTERNAL_STORAGE_DOCUMENTS_AUTHORITY = "com.android.externalstorage.documents"
    }
}
