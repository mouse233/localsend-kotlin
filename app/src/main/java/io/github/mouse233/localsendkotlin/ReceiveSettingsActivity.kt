package io.github.mouse233.localsendkotlin

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import io.github.mouse233.localsendkotlin.transfer.IncomingReceiveOptions
import io.github.mouse233.localsendkotlin.transfer.IncomingTransferManager
import io.github.mouse233.localsendkotlin.ui.FileTypeIcon
import io.github.mouse233.localsendkotlin.ui.SystemBars
import io.github.mouse233.localsendkotlin.ui.ThemeColors

/** Edits choices for the pending receive request without changing global settings. */
class ReceiveSettingsActivity : Activity() {
    private lateinit var fileList: LinearLayout
    private lateinit var directoryButton: Button
    private lateinit var gallerySwitch: Switch
    private lateinit var gallerySummary: TextView
    private val fileRows = mutableListOf<FileRow>()
    private var directoryUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SystemBars.apply(this)
        setContentView(R.layout.activity_receive_settings)
        ThemeColors.apply(this)
        fileList = findViewById(R.id.receive_settings_file_list)
        directoryButton = findViewById(R.id.receive_settings_directory_button)
        gallerySwitch = findViewById(R.id.receive_settings_gallery_switch)
        gallerySummary = findViewById(R.id.receive_settings_gallery_summary)
        directoryUri = intent.getStringExtra(EXTRA_DIRECTORY_URI)?.let(Uri::parse)
        directoryButton.setOnClickListener { chooseDirectory() }
        findViewById<android.view.View>(R.id.receive_settings_back_button).setOnClickListener { finishWithOptions() }
        gallerySwitch.isChecked = intent.getBooleanExtra(EXTRA_SAVE_TO_GALLERY, false)
        buildFileRows()
        updateDirectoryLabel()
    }

    private fun buildFileRows() {
        val ids = intent.getStringArrayListExtra(EXTRA_ALL_FILE_IDS).orEmpty()
        val names = intent.getStringArrayListExtra(EXTRA_FILE_NAMES).orEmpty()
        val originalNames = intent.getStringArrayListExtra(EXTRA_ORIGINAL_FILE_NAMES).orEmpty()
        val sizes = intent.getLongArrayExtra(EXTRA_FILE_SIZES) ?: LongArray(ids.size)
        val mimeTypes = intent.getStringArrayListExtra(EXTRA_FILE_MIME_TYPES).orEmpty()
        val selected = intent.getStringArrayListExtra(EXTRA_SELECTED_FILE_IDS).orEmpty().toSet()
        var hasMedia = false
        ids.forEachIndexed { index, id ->
            val mimeType = mimeTypes.getOrNull(index).orEmpty()
            val media = mimeType.startsWith("image/", true) || mimeType.startsWith("video/", true)
            hasMedia = hasMedia || media
            val originalName = originalNames.getOrNull(index) ?: names.getOrNull(index).orEmpty()
            val row = layoutInflater.inflate(R.layout.item_receive_settings_file, fileList, false)
            val checkBox = row.findViewById<CheckBox>(R.id.receive_settings_file_check).apply {
                isChecked = selected.isEmpty() || id in selected
            }
            val currentName = names.getOrNull(index).orEmpty().ifBlank { originalName }
            val name = row.findViewById<TextView>(R.id.receive_settings_file_name).apply { text = currentName }
            val details = row.findViewById<TextView>(R.id.receive_settings_file_details)
            val fileRow = FileRow(id, originalName, currentName, sizes.getOrNull(index) ?: 0L, name, details, checkBox)
            row.findViewById<android.widget.ImageView>(R.id.receive_settings_file_icon).setImageResource(FileTypeIcon.forMimeType(mimeType))
            updateDetails(fileRow)
            row.findViewById<android.view.View>(R.id.receive_settings_file_edit).setOnClickListener { showRenameDialog(fileRow) }
            fileList.addView(row)
            fileRows += fileRow
        }
        gallerySwitch.isEnabled = hasMedia
        gallerySummary.text = if (hasMedia) getString(R.string.incoming_settings_gallery_summary) else getString(R.string.incoming_settings_gallery_unavailable)
        if (!hasMedia) gallerySwitch.isChecked = false
    }

    private fun chooseDirectory() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, DIRECTORY_REQUEST)
    }

    @SuppressLint("WrongConstant")
    @Deprecated("Legacy Activity result API supports Android 5.0")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != DIRECTORY_REQUEST || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            @Suppress("WrongConstant")
            val persistableFlags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, persistableFlags)
            directoryUri = uri
            updateDirectoryLabel()
        } catch (_: SecurityException) {
            Toast.makeText(this, R.string.settings_receive_directory_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun finishWithOptions() {
        val selected = fileRows.filter { it.checkBox.isChecked }
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.incoming_settings_select_at_least_one, Toast.LENGTH_SHORT).show()
            return
        }
        val allIds = fileRows.map { it.id }
        val names = fileRows.map { it.currentName }
        setResult(RESULT_OK, Intent().apply {
            putStringArrayListExtra(EXTRA_ALL_FILE_IDS, ArrayList(allIds))
            putStringArrayListExtra(EXTRA_SELECTED_FILE_IDS, selected.map { it.id }.toCollection(ArrayList()))
            putStringArrayListExtra(EXTRA_FILE_NAMES, ArrayList(names))
            directoryUri?.let { putExtra(EXTRA_DIRECTORY_URI, it.toString()) }
            putExtra(EXTRA_SAVE_TO_GALLERY, gallerySwitch.isChecked)
        })
        finish()
    }

    @Deprecated("Legacy Activity back handling supports Android 5.0")
    override fun onBackPressed() {
        finishWithOptions()
    }

    private fun showRenameDialog(fileRow: FileRow) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
            setText(fileRow.currentName)
            setSelection(length())
            hint = getString(R.string.incoming_settings_file_name)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.incoming_settings_rename_file_title)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text.toString().trim()
            if (name.isBlank()) {
                input.error = getString(R.string.incoming_settings_file_name_required)
                return@setOnClickListener
            }
            fileRow.currentName = name
            fileRow.nameView.text = name
            updateDetails(fileRow)
            dialog.dismiss()
        }
    }

    private fun updateDetails(fileRow: FileRow) {
        val state = if (fileRow.currentName == fileRow.originalName) R.string.incoming_settings_unchanged else R.string.incoming_settings_changed
        fileRow.detailsView.text = getString(state, formatBytes(fileRow.size))
    }

    private fun updateDirectoryLabel() {
        directoryButton.text = directoryUri?.let { directoryName(it) } ?: getString(R.string.settings_default_receive_directory)
    }

    private fun directoryName(uri: Uri): String = try {
        DocumentsContract.getTreeDocumentId(uri).substringAfterLast('/').ifBlank { getString(R.string.settings_selected_receive_directory) }
    } catch (_: Exception) { getString(R.string.settings_selected_receive_directory) }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }

    private data class FileRow(
        val id: String,
        val originalName: String,
        var currentName: String,
        val size: Long,
        val nameView: TextView,
        val detailsView: TextView,
        val checkBox: CheckBox
    )

    companion object {
        const val EXTRA_SELECTED_FILE_IDS = "selected_file_ids"
        const val EXTRA_ALL_FILE_IDS = "all_file_ids"
        const val EXTRA_FILE_NAMES = "file_names"
        const val EXTRA_ORIGINAL_FILE_NAMES = "original_file_names"
        const val EXTRA_FILE_SIZES = "file_sizes"
        const val EXTRA_FILE_MIME_TYPES = "file_mime_types"
        const val EXTRA_DIRECTORY_URI = "directory_uri"
        const val EXTRA_SAVE_TO_GALLERY = "save_to_gallery"
        private const val DIRECTORY_REQUEST = 1101

        fun intent(context: Context, request: IncomingTransferManager.PrepareUploadRequest, options: IncomingReceiveOptions?): Intent {
            val entries = request.files.entries.toList()
            return Intent(context, ReceiveSettingsActivity::class.java).apply {
                putStringArrayListExtra(EXTRA_ALL_FILE_IDS, ArrayList(entries.map { it.key }))
                putStringArrayListExtra(EXTRA_SELECTED_FILE_IDS, ArrayList(options?.selectedFileIds ?: entries.map { it.key }))
                putStringArrayListExtra(EXTRA_FILE_NAMES, ArrayList(entries.map { options?.renamedFiles[it.key] ?: it.value.fileName }))
                putStringArrayListExtra(EXTRA_ORIGINAL_FILE_NAMES, ArrayList(entries.map { it.value.fileName }))
                putExtra(EXTRA_FILE_SIZES, entries.map { it.value.size }.toLongArray())
                putStringArrayListExtra(EXTRA_FILE_MIME_TYPES, ArrayList(entries.map { it.value.fileType }))
                options?.receiveDirectoryUri?.let { putExtra(EXTRA_DIRECTORY_URI, it.toString()) }
                putExtra(EXTRA_SAVE_TO_GALLERY, options?.saveMediaToGallery ?: false)
            }
        }
    }
}
