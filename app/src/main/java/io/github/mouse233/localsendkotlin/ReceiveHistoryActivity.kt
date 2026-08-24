package io.github.mouse233.localsendkotlin

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.mouse233.localsendkotlin.history.ReceiveHistoryStore
import io.github.mouse233.localsendkotlin.model.ReceiveHistoryEntry
import io.github.mouse233.localsendkotlin.ui.ReceiveHistoryAdapter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReceiveHistoryActivity : Activity() {
    private lateinit var store: ReceiveHistoryStore
    private lateinit var adapter: ReceiveHistoryAdapter
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receive_history)
        store = ReceiveHistoryStore(this)
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

    private fun reload() {
        Thread {
            val entries = store.list()
            runOnUiThread {
                adapter.submitEntries(entries)
                emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            }
        }.start()
    }

    private fun openFile(entry: ReceiveHistoryEntry) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(entry.uri, entry.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            Toast.makeText(this, R.string.open_file_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDirectory() {
        val downloadsUri = Uri.parse("content://media/external/downloads")
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(downloadsUri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(viewIntent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            } catch (_: Exception) {
                Toast.makeText(this, R.string.open_directory_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDetails(entry: ReceiveHistoryEntry) {
        val path = "${downloadsPath()}/LocalSend Kotlin/${entry.displayName}"
        val detailsView = layoutInflater.inflate(R.layout.dialog_receive_file_details, null)
        detailsView.findViewById<TextView>(R.id.file_info_name).text = entry.displayName
        detailsView.findViewById<TextView>(R.id.file_info_path).text = path
        detailsView.findViewById<TextView>(R.id.file_info_size).text = formatBytes(entry.size)
        detailsView.findViewById<TextView>(R.id.file_info_sender).text = entry.senderAlias
        detailsView.findViewById<TextView>(R.id.file_info_time).text = DATE_FORMAT.format(Date(entry.receivedAt))
        AlertDialog.Builder(this)
            .setTitle(R.string.file_info_title)
            .setView(detailsView)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_history_title)
            .setMessage(R.string.clear_history_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_history_records) { _, _ ->
                Thread { store.clear(); runOnUiThread(::reload) }.start()
            }
            .show()
    }

    private fun deleteEntry(entry: ReceiveHistoryEntry) {
        Thread { store.delete(entry.id); runOnUiThread(::reload) }.start()
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "%.1f KB".format(Locale.US, bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(Locale.US, bytes / (1024.0 * 1024.0))
        else -> "%.1f GB".format(Locale.US, bytes / (1024.0 * 1024.0 * 1024.0))
    }

    @Suppress("DEPRECATION")
    private fun downloadsPath(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "/storage/emulated/0/Download"
    } else {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
    }

    private companion object {
        val DATE_FORMAT = SimpleDateFormat("yyyy/M/d HH:mm", Locale.CHINA)
    }
}
