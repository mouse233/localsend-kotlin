package io.github.mouse233.localsendkotlin

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.Manifest
import android.annotation.TargetApi
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.res.ColorStateList
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Outline
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.Menu
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.mouse233.localsendkotlin.model.ReceivedFile
import io.github.mouse233.localsendkotlin.model.RemoteDevice
import io.github.mouse233.localsendkotlin.model.FavoriteDevice
import io.github.mouse233.localsendkotlin.model.ActiveTransferFile
import io.github.mouse233.localsendkotlin.model.PendingSendFile
import io.github.mouse233.localsendkotlin.model.PendingSendQueue
import io.github.mouse233.localsendkotlin.discovery.LocalNetworkAddress
import io.github.mouse233.localsendkotlin.discovery.ManualEndpoint
import io.github.mouse233.localsendkotlin.settings.AppSettings
import io.github.mouse233.localsendkotlin.sharing.ShareIntentParser
import io.github.mouse233.localsendkotlin.transfer.IncomingTransferManager
import io.github.mouse233.localsendkotlin.transfer.IncomingMessageLink
import io.github.mouse233.localsendkotlin.transfer.IncomingReceiveOptions
import io.github.mouse233.localsendkotlin.transfer.TransferService
import io.github.mouse233.localsendkotlin.transfer.TransferServiceState
import io.github.mouse233.localsendkotlin.ui.DeviceAdapter
import io.github.mouse233.localsendkotlin.ui.PendingSendAdapter
import io.github.mouse233.localsendkotlin.ui.SystemBars
import io.github.mouse233.localsendkotlin.ui.ThemeColors
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : LocalizedActivity(), TransferService.Listener {
    private lateinit var statusText: TextView
    private lateinit var openTransferButton: android.widget.Button
    private lateinit var pendingSendBar: android.view.View
    private lateinit var pendingSendSummary: TextView
    private lateinit var localEndpointDeviceName: TextView
    private lateinit var localEndpointBindLabel: TextView
    private lateinit var localEndpointAddresses: TextView
    private lateinit var contentActionMenu: android.view.ViewGroup
    private lateinit var contentActionFab: android.widget.ImageButton
    private var selectedFiles: List<Uri> = emptyList()
    private var selectedMessageText: String? = null
    private var contentMenuOpen = false
    private var appliedLanguage: String? = null
    private var transferService: TransferService? = null
    private var bound = false
    private val settings by lazy { AppSettings(this) }
    private val deviceAdapter = DeviceAdapter(::sendToDevice, ::showDeviceMenu) { fingerprint -> settings.isFavorite(fingerprint) }
    private var pendingVerificationRequest: IncomingTransferManager.PrepareUploadRequest? = null
    private var pendingVerificationDecision: ((IncomingReceiveOptions?) -> Unit)? = null
    private var pendingReceiveSettingsRequest: IncomingTransferManager.PrepareUploadRequest? = null
    private var pendingReceiveSettingsDecision: ((IncomingReceiveOptions?) -> Unit)? = null
    private var pendingReceiveSettingsOptions: IncomingReceiveOptions? = null
    private val contentExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingSendQueue = PendingSendQueue()
    private val pendingSendAdapter = PendingSendAdapter(::removePendingFile)
    private var pendingSendSheet: android.app.Dialog? = null
    private var appliedDarkMode: Boolean? = null

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
        appliedDarkMode = ThemeColors.isDark(this)
        SystemBars.apply(this)
        setContentView(R.layout.activity_main)
        ThemeColors.apply(this)
        val actionIconTint = ColorStateList.valueOf(ThemeColors.primaryColor(this))
        findViewById<ImageButton>(R.id.manual_send_button).imageTintList = actionIconTint
        findViewById<ImageButton>(R.id.refresh_button).imageTintList = actionIconTint
        findViewById<ImageButton>(R.id.favorites_button).imageTintList = actionIconTint
        statusText = findViewById(R.id.discovery_status)
        openTransferButton = findViewById(R.id.open_transfer_button)
        pendingSendBar = findViewById(R.id.pending_send_bar)
        pendingSendSummary = findViewById(R.id.pending_send_summary)
        localEndpointDeviceName = findViewById(R.id.local_endpoint_device_name)
        localEndpointBindLabel = findViewById(R.id.local_endpoint_bind_label)
        localEndpointAddresses = findViewById(R.id.local_endpoint_addresses)
        contentActionMenu = findViewById(R.id.content_action_menu)
        contentActionFab = findViewById(R.id.select_file_fab)
        appliedLanguage = AppSettings(this).language()
        findViewById<android.view.View>(R.id.history_button).setOnClickListener {
            startActivity(Intent(this, ReceiveHistoryActivity::class.java))
        }
        findViewById<android.view.View>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        openTransferButton.setOnClickListener { openTransferCenter() }
        pendingSendBar.setOnClickListener { showPendingSendSheet() }
        findViewById<RecyclerView>(R.id.device_list).apply { layoutManager = LinearLayoutManager(this@MainActivity); adapter = deviceAdapter }
        findViewById<android.view.View>(R.id.refresh_button).setOnClickListener { transferService?.refreshDevices() }
        findViewById<android.view.View>(R.id.manual_send_button).setOnClickListener { showManualSendDialog() }
        findViewById<android.view.View>(R.id.favorites_button).setOnClickListener { showFavoritesDialog() }
        contentActionFab.setOnClickListener { setContentActionMenuOpen(!contentMenuOpen) }
        configureFabShadow(contentActionFab)
        listOf(
            R.id.content_action_clipboard,
            R.id.content_action_text,
            R.id.content_action_media,
            R.id.content_action_folder,
            R.id.content_action_file
        ).forEach { configureFabShadow(findViewById(it)) }
        findViewById<android.view.View>(R.id.content_action_file).setOnClickListener { closeContentActionMenu(); chooseFile() }
        findViewById<android.view.View>(R.id.content_action_folder).setOnClickListener { closeContentActionMenu(); chooseFolder() }
        findViewById<android.view.View>(R.id.content_action_media).setOnClickListener { closeContentActionMenu(); chooseMedia() }
        findViewById<android.view.View>(R.id.content_action_text).setOnClickListener { closeContentActionMenu(); showTextInput() }
        findViewById<android.view.View>(R.id.content_action_clipboard).setOnClickListener { closeContentActionMenu(); chooseClipboard() }
        restorePendingSendState(savedInstanceState)
        onDevicesChanged(emptyList())
        updateLocalEndpoint()
        requestLegacyStoragePermission()
        requestNotificationPermission()
        startTransferService()
        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        SystemBars.apply(this)
        ThemeColors.apply(this)
        if (!bound) { bindService(Intent(this, TransferService::class.java), connection, BIND_AUTO_CREATE); bound = true }
    }

    override fun onResume() {
        super.onResume()
        val darkMode = ThemeColors.isDark(this)
        if (ThemeColors.needsActivityRecreate(appliedDarkMode, darkMode)) {
            appliedDarkMode = darkMode
            recreate()
            return
        }
        appliedDarkMode = darkMode
        SystemBars.apply(this)
        ThemeColors.apply(this)
        val language = AppSettings(this).language()
        if (language != appliedLanguage) {
            appliedLanguage = language
            recreate()
            return
        }
        updateLocalEndpoint()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            ThemeColors.apply(this)
            SystemBars.apply(this)
        }
    }

    private fun handleShareIntent(intent: Intent?) {
        val sharedContent = intent?.let(ShareIntentParser::parse) ?: return
        if (sharedContent.uris.isNotEmpty()) {
            persistReadPermissions(sharedContent.uris)
            setSelectedFiles(sharedContent.uris, sharedContent.text)
        } else {
            sharedContent.text?.let(::createOutgoingText)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (selectedFiles.isNotEmpty()) {
            outState.putParcelableArrayList(KEY_PENDING_SEND_URIS, ArrayList(selectedFiles))
            selectedMessageText?.let { outState.putString(KEY_PENDING_SEND_MESSAGE, it) }
        }
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        transferService?.removeListener(this)
        if (bound) { unbindService(connection); bound = false }
        super.onStop()
    }

    override fun onDestroy() {
        pendingSendSheet?.dismiss()
        contentExecutor.shutdownNow()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    @Deprecated("Legacy Activity back handling supports Android 5.0")
    override fun onBackPressed() {
        if (contentMenuOpen) {
            closeContentActionMenu()
            return
        }
        super.onBackPressed()
    }

    @Deprecated("Legacy Activity result API supports Android 5.0")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VERIFICATION_REQUEST) {
            val request = pendingVerificationRequest
            val decide = pendingVerificationDecision
            pendingVerificationRequest = null
            pendingVerificationDecision = null
            if (request != null && decide != null) {
                if (request.messageText() != null) showIncomingMessage(request, decide)
                else showIncomingRequest(request, decide, pendingReceiveSettingsOptions)
            }
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
        if ((requestCode == FILE_REQUEST || requestCode == MEDIA_REQUEST) && resultCode == RESULT_OK) {
            val files = buildList {
                data?.clipData?.let { clips -> for (index in 0 until clips.itemCount) add(clips.getItemAt(index).uri) }
                    ?: data?.data?.let(::add)
            }
            persistReadPermissions(files)
            setSelectedFiles(files)
            return
        }
        if (requestCode == FOLDER_REQUEST && resultCode == RESULT_OK) {
            val treeUri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            statusText.text = getString(R.string.content_action_folder_scanning)
            contentExecutor.execute {
                try {
                    val files = collectFolderFiles(treeUri)
                    mainHandler.post {
                        if (files.isEmpty()) {
                            Toast.makeText(this, R.string.content_action_folder_empty, Toast.LENGTH_SHORT).show()
                        } else {
                            setSelectedFiles(files)
                        }
                    }
                } catch (_: Exception) {
                    mainHandler.post { Toast.makeText(this, R.string.content_action_folder_failed, Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    private fun chooseFile() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        type = "*/*"
    }, FILE_REQUEST)

    private fun chooseMedia() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                type = "*/*"
                putExtra(
                    MediaStore.EXTRA_PICK_IMAGES_MAX,
                    minOf(999, MediaStore.getPickImagesMaxLimit())
                )
            }
        } else {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            }
        }
        startActivityForResult(intent, MEDIA_REQUEST)
    }

    private fun chooseFolder() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }, FOLDER_REQUEST)

    private fun setSelectedFiles(files: List<Uri>, messageText: String? = null) {
        selectedFiles = files
        selectedMessageText = messageText
        pendingSendQueue.clear()
        if (files.isNotEmpty()) {
            statusText.text = getString(R.string.files_selected, files.size)
            refreshPendingSendBar()
            contentExecutor.execute {
                val loaded = files.map { pendingFile(it) }
                mainHandler.post {
                    if (selectedFiles == files) {
                        pendingSendQueue.replace(loaded)
                        refreshPendingSendBar()
                        pendingSendSheet?.findViewById<RecyclerView>(R.id.pending_send_list)?.let {
                            pendingSendAdapter.submitFiles(loaded)
                            resizePendingSendList(it)
                        }
                    }
                }
            }
        } else {
            refreshPendingSendBar()
        }
    }

    @Suppress("DEPRECATION")
    private fun restorePendingSendState(state: Bundle?) {
        val files = state?.getParcelableArrayList<Uri>(KEY_PENDING_SEND_URIS).orEmpty()
        if (files.isNotEmpty()) {
            setSelectedFiles(files, state?.getString(KEY_PENDING_SEND_MESSAGE))
        }
    }

    private fun pendingFile(uri: Uri): PendingSendFile {
        var name = uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { getString(R.string.shared_file) }
        var size = -1L
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(0) ?: name
                size = cursor.getLong(1)
            }
        }
        return PendingSendFile(uri, name, size)
    }

    private fun refreshPendingSendBar() {
        val visible = selectedFiles.isNotEmpty()
        pendingSendBar.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
        if (!visible) return
        val pendingFiles = pendingSendQueue.snapshot()
        val total = pendingFiles.sumOf { it.size }
        pendingSendSummary.text = if (pendingFiles.size == selectedFiles.size && pendingFiles.all { it.size >= 0 }) {
            getString(R.string.pending_send_summary_with_size, selectedFiles.size, formatBytes(total))
        } else {
            getString(R.string.pending_send_summary, selectedFiles.size)
        }
    }

    private fun removePendingFile(file: PendingSendFile) {
        selectedFiles = selectedFiles.filterNot { it == file.uri }
        pendingSendQueue.remove(file.uri)
        if (selectedFiles.size != 1) selectedMessageText = null
        refreshPendingSendBar()
        pendingSendSheet?.findViewById<RecyclerView>(R.id.pending_send_list)?.let {
            pendingSendAdapter.submitFiles(pendingSendQueue.snapshot())
            resizePendingSendList(it)
        }
        if (selectedFiles.isEmpty()) pendingSendSheet?.dismiss()
    }

    private fun clearPendingFiles() {
        selectedFiles = emptyList()
        selectedMessageText = null
        pendingSendQueue.clear()
        refreshPendingSendBar()
        pendingSendSheet?.dismiss()
    }

    private fun showPendingSendSheet() {
        if (selectedFiles.isEmpty()) return
        pendingSendSheet?.dismiss()
        val content = layoutInflater.inflate(R.layout.dialog_pending_send, null)
        val dialog = android.app.Dialog(this)
        dialog.setContentView(content)
        dialog.setCanceledOnTouchOutside(true)
        content.findViewById<TextView>(R.id.pending_send_summary).text = pendingSendSummary.text
        content.findViewById<RecyclerView>(R.id.pending_send_list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = pendingSendAdapter
            itemAnimator = null
            pendingSendAdapter.submitFiles(pendingSendQueue.snapshot())
            resizePendingSendList(this)
        }
        content.findViewById<android.view.View>(R.id.pending_send_close).setOnClickListener { dialog.dismiss() }
        content.findViewById<android.view.View>(R.id.pending_send_done).setOnClickListener { dialog.dismiss() }
        content.findViewById<android.view.View>(R.id.pending_send_clear).setOnClickListener { clearPendingFiles() }
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply {
                gravity = Gravity.BOTTOM
                dimAmount = 0.20f
            }
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        ThemeColors.apply(content)
        pendingSendSheet = dialog
        dialog.setOnDismissListener { if (pendingSendSheet === dialog) pendingSendSheet = null }
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun resizePendingSendList(list: RecyclerView) {
        val density = resources.displayMetrics.density
        val rowHeight = (48 * density).toInt()
        val listPadding = (8 * density).toInt()
        val maximumHeight = (resources.displayMetrics.heightPixels * 9 / 16).toInt()
        list.layoutParams = list.layoutParams.apply {
            height = (pendingSendQueue.snapshot().size * rowHeight + listPadding)
                .coerceAtMost(maximumHeight)
        }
    }

    private fun openTransferCenter() {
        startActivity(Intent(this, TransferCenterActivity::class.java))
    }

    private fun persistReadPermissions(uris: List<Uri>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) uris.forEach { uri ->
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
        }
    }

    private fun collectFolderFiles(treeUri: Uri): List<Uri> {
        val files = mutableListOf<Uri>()
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        fun visit(documentId: String) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val typeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val childId = cursor.getString(idColumn)
                    val childType = cursor.getString(typeColumn)
                    if (childType == DocumentsContract.Document.MIME_TYPE_DIR) visit(childId)
                    else files += DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                }
            }
        }
        visit(rootDocumentId)
        return files
    }

    private fun chooseClipboard() {
        @Suppress("DEPRECATION")
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(this, R.string.content_action_no_clipboard, Toast.LENGTH_SHORT).show()
            return
        }
        val text = clip.getItemAt(0).coerceToText(this).toString().takeIf { it.isNotBlank() }
        if (text != null) {
            createOutgoingText(text)
            return
        }
        val uris = buildList { for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let(::add) }
        if (uris.isEmpty()) {
            Toast.makeText(this, R.string.content_action_no_clipboard, Toast.LENGTH_SHORT).show()
            return
        }
        contentExecutor.execute {
            try {
                val copied = uris.mapIndexed { index, uri -> copyToCache(uri, "clipboard_$index") }
                mainHandler.post { setSelectedFiles(copied) }
            } catch (_: Exception) {
                mainHandler.post { Toast.makeText(this, R.string.content_action_no_clipboard, Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun showTextInput() {
        val content = layoutInflater.inflate(R.layout.dialog_text_message, null)
        val input = content.findViewById<EditText>(R.id.text_message_editor)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.content_action_text_title)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.show()
        ThemeColors.apply(dialog)
        val customPanelId = resources.getIdentifier("customPanel", "id", "android")
        if (customPanelId != 0) dialog.findViewById<android.view.View>(customPanelId)?.minimumHeight = 0
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val text = input.text.toString()
            if (text.isBlank()) {
                input.error = getString(R.string.content_action_text_empty)
                return@setOnClickListener
            }
            dialog.dismiss()
            createOutgoingText(text)
        }
    }

    private fun createOutgoingText(text: String) {
        contentExecutor.execute {
            try {
                val file = File(cacheDir, "message_${UUID.randomUUID()}.txt")
                file.writeText(text, Charsets.UTF_8)
                val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
                mainHandler.post { setSelectedFiles(listOf(uri), text) }
            } catch (_: Exception) {
                mainHandler.post { Toast.makeText(this, R.string.content_action_text_empty, Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun copyToCache(sourceUri: Uri, prefix: String): Uri {
        var name = sourceUri.lastPathSegment?.substringAfterLast('/').orEmpty()
        contentResolver.query(sourceUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) name = cursor.getString(0).orEmpty()
        }
        if (name.isBlank()) name = "$prefix.bin"
        val file = File(cacheDir, "${prefix}_${UUID.randomUUID()}_${File(name).name}")
        contentResolver.openInputStream(sourceUri)?.use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }
            ?: throw IllegalStateException("无法读取剪贴板文件")
        return FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
    }

    private fun closeContentActionMenu() {
        if (contentMenuOpen) setContentActionMenuOpen(false)
    }

    private fun configureFabShadow(button: android.widget.ImageButton) {
        val restingElevation = 6f * resources.displayMetrics.density
        val pressedElevation = 12f * resources.displayMetrics.density
        button.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: android.view.View, outline: Outline) {
                val radius = minOf(view.measuredWidth, view.measuredHeight) / 2f
                outline.setRoundRect(0, 0, view.measuredWidth, view.measuredHeight, radius)
            }
        }
        button.elevation = restingElevation
        button.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            view.invalidateOutline()
        }
        button.post { button.invalidateOutline() }
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.elevation = pressedElevation
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.elevation = restingElevation
            }
            false
        }
    }

    private fun setContentActionMenuOpen(open: Boolean) {
        contentMenuOpen = open
        cancelContentActionItemAnimations()
        contentActionFab.animate().cancel()
        contentActionFab.animate().setListener(null)
        val interpolator = android.view.animation.PathInterpolator(0.4f, 0f, 0.2f, 1f)
        if (open) {
            contentActionFab.setImageResource(R.drawable.ic_close)
            contentActionFab.contentDescription = getString(R.string.content_action_close)
            contentActionMenu.visibility = android.view.View.VISIBLE
            contentActionMenu.alpha = 1f
            contentActionFab.animate().rotation(45f).setDuration(200L).setInterpolator(interpolator).start()
            animateContentActionItems(true, interpolator)
        } else {
            contentActionFab.setImageResource(R.drawable.ic_add)
            contentActionFab.contentDescription = getString(R.string.content_action_add)
            contentActionFab.animate().rotation(0f).setDuration(200L).setInterpolator(interpolator).start()
            animateContentActionItems(false, interpolator)
        }
    }

    private fun animateContentActionItems(
        open: Boolean,
        interpolator: android.view.animation.Interpolator
    ) {
        val restingElevation = 6f * resources.displayMetrics.density
        val itemCount = contentActionMenu.childCount
        for (index in 0 until itemCount) {
            val row = contentActionMenu.getChildAt(index) as? android.view.ViewGroup ?: continue
            val label = row.getChildAt(0) as? TextView
            val button = row.getChildAt(1) as? android.widget.ImageButton ?: continue
            val delay = if (open) 15L * (itemCount - index) else 15L * index
            if (open) {
                label?.alpha = 0f
                button.alpha = 0f
                button.scaleX = 0f
                button.scaleY = 0f
                button.translationZ = -restingElevation
            }
            label?.animate()?.alpha(if (open) 1f else 0f)
                ?.setStartDelay(delay)
                ?.setDuration(150L)
                ?.setInterpolator(interpolator)
                ?.start()
            val animation = button.animate()
                .alpha(if (open) 1f else 0f)
                .scaleX(if (open) 1f else 0f)
                .scaleY(if (open) 1f else 0f)
                .translationZ(if (open) 0f else -restingElevation)
                .setStartDelay(delay)
                .setDuration(150L)
                .setInterpolator(interpolator)
            if (!open && index == itemCount - 1) {
                animation.setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (!contentMenuOpen) {
                            contentActionMenu.visibility = android.view.View.GONE
                            resetContentActionItems()
                        }
                    }
                })
            }
            animation.start()
        }
    }

    private fun cancelContentActionItemAnimations() {
        for (index in 0 until contentActionMenu.childCount) {
            val row = contentActionMenu.getChildAt(index) as? android.view.ViewGroup ?: continue
            row.getChildAt(0)?.animate()?.cancel()
            row.getChildAt(1)?.animate()?.cancel()
            row.getChildAt(1)?.animate()?.setListener(null)
        }
    }

    private fun resetContentActionItems() {
        for (index in 0 until contentActionMenu.childCount) {
            val row = contentActionMenu.getChildAt(index) as? android.view.ViewGroup ?: continue
            (row.getChildAt(0) as? TextView)?.alpha = 1f
            (row.getChildAt(1) as? android.widget.ImageButton)?.let { button ->
                button.alpha = 1f
                button.scaleX = 1f
                button.scaleY = 1f
                button.translationZ = 0f
            }
        }
    }

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
        ThemeColors.apply(dialog)
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
            val files = selectedFiles
            val message = selectedMessageText
            transferService?.let {
                clearPendingFiles()
                it.sendManual(files, endpoint, message)
                openTransferCenter()
            } ?: Toast.makeText(this, R.string.service_starting, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFavoritesDialog() {
        val favorites = settings.favoriteDevices()
        if (favorites.isEmpty()) {
            val dialog = AlertDialog.Builder(this)
                .setTitle(R.string.favorites)
                .setMessage(R.string.favorites_empty)
                .setPositiveButton(R.string.close, null)
                .create()
            dialog.show()
            ThemeColors.apply(dialog)
            return
        }

        val content = layoutInflater.inflate(R.layout.dialog_favorite_devices, null)
        val list = content.findViewById<LinearLayout>(R.id.favorite_devices_list)
        val rows = mutableListOf<Pair<android.view.View, FavoriteDevice>>()
        favorites.forEach { favorite ->
            val row = layoutInflater.inflate(R.layout.item_favorite_device, list, false)
            row.findViewById<TextView>(R.id.favorite_device_text).text = favoriteDisplayName(favorite)
            rows += row to favorite
            list.addView(row)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.favorites)
            .setView(content)
            .setNegativeButton(R.string.close, null)
            .create()
        dialog.show()
        ThemeColors.apply(dialog)
        val actionIconTint = ColorStateList.valueOf(ThemeColors.primaryColor(this))
        rows.forEach { (row, favorite) ->
            row.setOnClickListener {
                dialog.dismiss()
                sendToFavorite(favorite)
            }
            row.findViewById<ImageButton>(R.id.favorite_edit_button).apply {
                imageTintList = actionIconTint
                setOnClickListener {
                    dialog.dismiss()
                    showEditFavoriteDialog(favorite)
                }
            }
        }
    }

    private fun showEditFavoriteDialog(favorite: FavoriteDevice) {
        val content = layoutInflater.inflate(R.layout.dialog_edit_favorite, null)
        val nameInput = content.findViewById<EditText>(R.id.favorite_name_editor).apply {
            setText(favorite.alias)
            setSelection(length())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val addressInput = content.findViewById<EditText>(R.id.favorite_address_editor).apply {
            setText(favorite.address)
            setSelection(length())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val portInput = content.findViewById<EditText>(R.id.favorite_port_editor).apply {
            setText(favorite.port.toString())
            setSelection(length())
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.edit_favorite_device)
            .setView(content)
            .setNeutralButton(R.string.delete_favorite_device, null)
            .setNegativeButton(android.R.string.cancel) { _, _ -> showFavoritesDialog() }
            .setPositiveButton(R.string.save, null)
            .create()
        dialog.show()
        ThemeColors.apply(dialog)
        val customPanelId = resources.getIdentifier("customPanel", "id", "android")
        if (customPanelId != 0) dialog.findViewById<android.view.View>(customPanelId)?.minimumHeight = 0

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val alias = nameInput.text.toString().trim()
            val address = addressInput.text.toString().trim()
            val port = portInput.text.toString().trim().toIntOrNull()
            when {
                alias.isEmpty() -> {
                    nameInput.error = getString(R.string.favorite_name_required)
                    return@setOnClickListener
                }
                address.isEmpty() -> {
                    addressInput.error = getString(R.string.favorite_address_required)
                    return@setOnClickListener
                }
                port == null || port !in 1..65535 -> {
                    portInput.error = getString(R.string.favorite_port_invalid)
                    return@setOnClickListener
                }
            }
            settings.updateFavorite(favorite, alias, address, port)
            dialog.dismiss()
            showFavoritesDialog()
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            dialog.dismiss()
            showDeleteFavoriteConfirmation(favorite)
        }
    }

    private fun showDeleteFavoriteConfirmation(favorite: FavoriteDevice) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.delete_favorite_device)
            .setMessage(getString(R.string.delete_favorite_device_message, favorite.alias))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_favorite_device) { _, _ ->
                settings.removeFavorite(favorite.fingerprint)
                deviceAdapter.refreshFavoriteStates()
                showFavoritesDialog()
            }
            .create()
        dialog.show()
        ThemeColors.apply(dialog)
    }

    private fun favoriteDisplayName(favorite: FavoriteDevice): String = getString(
        R.string.favorite_device_item_format,
        favorite.alias,
        favorite.address,
        favorite.port
    )

    private fun sendToFavorite(favorite: FavoriteDevice) {
        sendToDevice(
            RemoteDevice(
                alias = favorite.alias,
                deviceModel = null,
                deviceType = null,
                fingerprint = favorite.fingerprint,
                address = favorite.address,
                port = favorite.port,
                protocol = favorite.protocol,
                downloadEnabled = true
            )
        )
    }

    private fun sendToDevice(device: RemoteDevice) {
        if (selectedFiles.isEmpty()) { Toast.makeText(this, R.string.select_file_first, Toast.LENGTH_SHORT).show(); return }
        val files = selectedFiles
        val message = selectedMessageText
        transferService?.let {
            pendingSendSheet?.dismiss()
            clearPendingFiles()
            it.send(files, device, message)
            openTransferCenter()
        } ?: Toast.makeText(this, R.string.service_starting, Toast.LENGTH_SHORT).show()
    }

    private fun verifyDevice(device: RemoteDevice) {
        startActivity(Intent(this, VerificationActivity::class.java).putExtra(VerificationActivity.EXTRA_FINGERPRINT, device.fingerprint))
    }

    private fun showDeviceMenu(anchor: android.view.View, device: RemoteDevice) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(
            Menu.NONE,
            FAVORITE_MENU_ID,
            Menu.NONE,
            getString(if (settings.isFavorite(device.fingerprint)) R.string.unfavorite_device else R.string.favorite_device)
        )
        popup.menu.add(Menu.NONE, VERIFY_MENU_ID, Menu.NONE, R.string.verification_title)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                FAVORITE_MENU_ID -> {
                    settings.toggleFavorite(device)
                    deviceAdapter.refreshFavoriteStates()
                    true
                }
                VERIFY_MENU_ID -> {
                    verifyDevice(device)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun startTransferService() {
        // This is called while the Activity is visible. Starting normally avoids
        // Xiaomi Android 16 treating the foreground-service promotion as late.
        TransferServiceState.start(this)
    }

    override fun onDevicesChanged(devices: List<RemoteDevice>) {
        devices.forEach(settings::refreshFavorite)
        deviceAdapter.submitDevices(devices)
        if (selectedFiles.isEmpty()) statusText.text = if (devices.isEmpty()) getString(R.string.discovery_scanning) else resources.getQuantityString(R.plurals.device_count, devices.size, devices.size)
    }
    override fun onDiscoveryError(message: String) { statusText.text = getString(R.string.discovery_error, message) }
    override fun onIncomingSessionPrepared(sessionId: String, request: IncomingTransferManager.PrepareUploadRequest) {
        showActiveTransferShortcut()
    }
    override fun onOutgoingSessionPreparing(sessionId: String, files: List<ActiveTransferFile>) {
        showActiveTransferShortcut()
    }
    override fun onOutgoingChecksumProgress(sessionId: String, current: Int, total: Int) {
        showActiveTransferShortcut()
    }
    override fun onOutgoingSessionPrepared(sessionId: String, files: List<ActiveTransferFile>) {
        showActiveTransferShortcut()
    }
    override fun onOutgoingSessionStarted(preparationSessionId: String, sessionId: String, files: List<ActiveTransferFile>) {
        showActiveTransferShortcut()
    }
    override fun onActiveTransfersRestored(files: List<ActiveTransferFile>) {
        if (files.isNotEmpty()) showActiveTransferShortcut()
    }
    override fun onUploadStatus(message: String) = Unit
    override fun onPinRequired(device: RemoteDevice, attempt: Int, reply: (String?) -> Unit) {
        if (isFinishing || isDestroyed) {
            reply(null)
            return
        }
        val content = layoutInflater.inflate(R.layout.dialog_pin, null)
        val message = content.findViewById<TextView>(R.id.pin_dialog_message)
        val input = content.findViewById<EditText>(R.id.pin_dialog_editor)
        input.inputType = InputType.TYPE_CLASS_TEXT
        message.text = getString(R.string.pin_required_message, device.alias, attempt)
        var replied = false
        fun respond(pin: String?) {
            if (replied) return
            replied = true
            reply(pin)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.pin_required_title)
            .setView(content)
            .setNegativeButton(android.R.string.cancel) { _, _ -> respond(null) }
            .setPositiveButton(R.string.submit) { _, _ -> respond(input.text.toString()) }
            .setOnCancelListener { respond(null) }
            .create()
        dialog.show()
        ThemeColors.apply(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val pin = input.text.toString().trim()
            if (pin.isEmpty()) {
                input.error = getString(R.string.invalid_pin)
                return@setOnClickListener
            }
            dialog.dismiss()
            respond(pin)
        }
    }
    override fun onTransferStateRestored(title: String, percent: Int) {
        showActiveTransferShortcut()
    }
    override fun onUploadProgress(fileName: String, fileIndex: Int, fileCount: Int, sent: Long, total: Long, totalSent: Long, totalBytes: Long) {
        showActiveTransferShortcut()
    }
    override fun onUploadCompleted(names: List<String>) = showActiveTransferShortcut()
    override fun onUploadError(message: String) = showActiveTransferShortcut()
    override fun onTransferFinished(message: String) = showActiveTransferShortcut()

    override fun onIncomingTransferRequest(request: IncomingTransferManager.PrepareUploadRequest, decide: (IncomingReceiveOptions?) -> Unit) {
        if (isFinishing || isDestroyed) { decide(null); return }
        if (request.messageText() != null) {
            // Keep the prepare request pending until the user finishes with
            // the message. This is when LocalSend sends the 204 response.
            showIncomingMessage(request, decide)
            return
        }
        showIncomingRequest(request, decide, IncomingReceiveOptions.forAll(request, AppSettings(this)))
    }

    private fun showIncomingMessage(request: IncomingTransferManager.PrepareUploadRequest, decide: (IncomingReceiveOptions?) -> Unit) {
        val message = request.messageText() ?: return
        val linkUri = IncomingMessageLink.detect(message)?.let(Uri::parse)
        val content = layoutInflater.inflate(R.layout.dialog_incoming_request, null)
        content.findViewById<TextView>(R.id.incoming_file_list).apply {
            text = message
            setTextIsSelectable(true)
        }
        var historyRecorded = false
        fun finishMessage() {
            if (!historyRecorded) {
                historyRecorded = true
                transferService?.recordReceivedMessage(message, request.info.alias)
            }
            decide(IncomingReceiveOptions.forAll(request, AppSettings(this)))
        }
        fun copyMessage() {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("LocalSend", message))
            finishMessage()
        }
        fun openLink() {
            val uri = linkUri ?: return
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
                finishMessage()
            } catch (_: Exception) {
                Toast.makeText(this, R.string.open_external_link_failed, Toast.LENGTH_SHORT).show()
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.incoming_message_title, request.info.alias))
            .setView(content)
            .setNegativeButton(R.string.close) { _, _ -> finishMessage() }
            .setNeutralButton(R.string.verification_title) { _, _ ->
                pendingVerificationRequest = request
                pendingVerificationDecision = decide
                startActivityForResult(
                    Intent(this, VerificationActivity::class.java)
                        .putExtra(VerificationActivity.EXTRA_FINGERPRINT, request.info.fingerprint),
                    VERIFICATION_REQUEST
                )
            }
            .setPositiveButton(if (linkUri == null) R.string.content_action_clipboard else R.string.open_file) { _, _ ->
                if (linkUri == null) copyMessage() else openLink()
            }
            .setOnCancelListener { finishMessage() }
            .create()
        dialog.show()
        if (linkUri != null) {
            val openButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val buttonPanel = openButton?.parent as? android.view.ViewGroup
            if (openButton != null && buttonPanel != null) {
                val copyButton = android.widget.Button(this, null, android.R.attr.buttonBarNeutralButtonStyle).apply {
                    setText(R.string.content_action_clipboard)
                    isAllCaps = false
                    setOnClickListener {
                        dialog.dismiss()
                        copyMessage()
                    }
                }
                buttonPanel.addView(copyButton, buttonPanel.indexOfChild(openButton))
            }
        }
        ThemeColors.apply(dialog)
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
            .setPositiveButton(R.string.incoming_request_accept) { _, _ ->
                decide(options)
                openTransferCenter()
            }
            .setOnCancelListener { decide(null) }.create()
        dialog.show()
        val settingsButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        val buttonPanel = settingsButton?.parent as? android.view.ViewGroup
        if (settingsButton != null && buttonPanel != null) {
            val verifyButton = android.widget.Button(this, null, android.R.attr.buttonBarNeutralButtonStyle).apply {
                setText(R.string.verification_title)
                isAllCaps = true
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
        ThemeColors.apply(dialog)
    }
    override fun onFileReceiveProgress(file: ActiveTransferFile) {
        showActiveTransferShortcut()
    }
    override fun onFileSendProgress(file: ActiveTransferFile) {
        showActiveTransferShortcut()
    }
    override fun onFileReceiveCancelled(file: ActiveTransferFile, sessionComplete: Boolean) {
        if (sessionComplete) showActiveTransferShortcut()
    }
    override fun onFileReceived(sessionId: String, fileId: String, file: ReceivedFile, sessionComplete: Boolean) {
        val message = getString(R.string.download_completed, file.displayName)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        if (sessionComplete) showActiveTransferShortcut()
    }
    override fun onIncomingSessionCompleted(sessionId: String) { showActiveTransferShortcut() }
    override fun onOutgoingSessionCompleted(sessionId: String) { showActiveTransferShortcut() }

    private fun showActiveTransferShortcut() {
        openTransferButton.visibility = android.view.View.VISIBLE
    }

    private fun updateLocalEndpoint() {
        val settings = AppSettings(this)
        localEndpointDeviceName.text = settings.deviceName()
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

    private companion object {
        const val KEY_PENDING_SEND_URIS = "pending_send_uris"
        const val KEY_PENDING_SEND_MESSAGE = "pending_send_message"
        const val FILE_REQUEST = 1001
        const val LEGACY_STORAGE_PERMISSION_REQUEST = 1002
        const val NOTIFICATION_PERMISSION_REQUEST = 1003
        const val VERIFICATION_REQUEST = 1004
        const val RECEIVE_SETTINGS_REQUEST = 1005
        const val MEDIA_REQUEST = 1006
        const val FOLDER_REQUEST = 1007
        const val FAVORITE_MENU_ID = 1008
        const val VERIFY_MENU_ID = 1009
    }
}
