package io.github.mouse233.localsendkotlin.transfer

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import io.github.mouse233.localsendkotlin.BuildConfig
import io.github.mouse233.localsendkotlin.model.ReceivedFile
import io.github.mouse233.localsendkotlin.settings.AppSettings
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/** Saves received files in shared Downloads while respecting scoped-storage rules. */
class IncomingFileStore(context: Context) {
    private val appContext = context.applicationContext
    @PublishedApi
    internal val resolver = appContext.contentResolver
    private val settings = AppSettings(appContext)

    fun create(
        displayName: String,
        mimeType: String,
        size: Long,
        receiveDirectoryUri: Uri? = settings.receiveDirectoryUri(),
        saveMediaToGallery: Boolean = false
    ): Destination {
        val safeName = File(displayName).name.ifBlank { "received-file" }
        if (saveMediaToGallery && isGalleryMedia(mimeType)) {
            try {
                return createInGallery(safeName, mimeType, size)
            } catch (_: Exception) {
                // Fall back to the selected receive directory when gallery access fails.
            }
        }
        receiveDirectoryUri?.let { treeUri ->
            try {
                return createInSelectedDirectory(treeUri, safeName, mimeType, size)
            } catch (_: Exception) {
                // The user may have revoked access or removed the selected directory.
                settings.clearReceiveDirectory()
            }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_PATH)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Unable to create download entry")
            Destination(ReceivedFile(safeName, uri, mimeType, size), null, mediaStorePending = true)
        } else {
            val folder = legacyFolder().apply { mkdirs() }
            val file = uniqueFile(folder, safeName)
            val uri = FileProvider.getUriForFile(appContext, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
            Destination(ReceivedFile(file.name, uri, mimeType, size), file, mediaStorePending = false)
        }
    }

    private fun createInSelectedDirectory(treeUri: Uri, displayName: String, mimeType: String, size: Long): Destination {
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )
        val uri = DocumentsContract.createDocument(resolver, parentUri, mimeType, displayName)
            ?: throw IOException("Unable to create file in selected directory")
        return Destination(ReceivedFile(displayName, uri, mimeType, size), null, mediaStorePending = false)
    }

    private fun createInGallery(displayName: String, mimeType: String, size: Long): Destination {
        val isImage = mimeType.startsWith("image/", ignoreCase = true)
        val collection = if (isImage) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val relativePath = if (isImage) "Pictures/$GALLERY_FOLDER/" else "Movies/$GALLERY_FOLDER/"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: throw IOException("Unable to create gallery entry")
            return Destination(ReceivedFile(displayName, uri, mimeType, size), null, mediaStorePending = true)
        }
        val folderType = if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
        val folder = File(Environment.getExternalStoragePublicDirectory(folderType), GALLERY_FOLDER).apply { mkdirs() }
        val file = uniqueFile(folder, displayName)
        val uri = FileProvider.getUriForFile(appContext, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        return Destination(ReceivedFile(file.name, uri, mimeType, size), file, mediaStorePending = false)
    }

    private fun isGalleryMedia(mimeType: String): Boolean =
        mimeType.startsWith("image/", ignoreCase = true) || mimeType.startsWith("video/", ignoreCase = true)

    /** Opens the destination, runs the writer, and always closes the stream before returning. */
    @SuppressLint("Recycle")
    internal inline fun <T> withOutput(destination: Destination, writer: (OutputStream) -> T): T {
        val output = destination.legacyFile?.let(::FileOutputStream)
            ?: resolver.openOutputStream(destination.file.uri, "w")
            ?: throw IOException("Unable to open download entry")
        return output.use(writer)
    }

    fun complete(destination: Destination) {
        if (destination.mediaStorePending) {
            resolver.update(destination.file.uri, ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }, null, null)
        } else {
            destination.legacyFile?.let { file ->
                MediaScannerConnection.scanFile(appContext, arrayOf(file.absolutePath), arrayOf(destination.file.mimeType), null)
            }
        }
    }

    fun discard(destination: Destination) {
        destination.legacyFile?.delete() ?: resolver.delete(destination.file.uri, null, null)
    }

    fun listReceivedFiles(): List<ReceivedFile> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listModernFiles() else listLegacyFiles()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun listModernFiles(): List<ReceivedFile> {
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.MIME_TYPE,
            MediaStore.Downloads.SIZE
        )
        val files = mutableListOf<ReceivedFile>()
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Downloads.RELATIVE_PATH} = ?",
            arrayOf(RELATIVE_PATH),
            "${MediaStore.Downloads.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
            while (cursor.moveToNext()) {
                files += ReceivedFile(
                    cursor.getString(nameColumn) ?: "received-file",
                    Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(idColumn).toString()),
                    cursor.getString(typeColumn) ?: "application/octet-stream",
                    cursor.getLong(sizeColumn)
                )
            }
        }
        return files
    }

    private fun listLegacyFiles(): List<ReceivedFile> = legacyFolder().listFiles()
        ?.filter { it.isFile }
        ?.sortedByDescending { it.lastModified() }
        ?.map { file ->
            ReceivedFile(
                file.name,
                FileProvider.getUriForFile(appContext, "${BuildConfig.APPLICATION_ID}.fileprovider", file),
                appContext.contentResolver.getType(Uri.fromFile(file)) ?: "application/octet-stream",
                file.length()
            )
        }
        ?: emptyList()

    private fun legacyFolder(): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        FOLDER_NAME
    )

    private fun uniqueFile(folder: File, name: String): File {
        var candidate = File(folder, name)
        var index = 1
        while (candidate.exists()) candidate = File(folder, "${index++}_$name")
        return candidate
    }

    data class Destination(val file: ReceivedFile, val legacyFile: File?, val mediaStorePending: Boolean)

    private companion object {
        const val FOLDER_NAME = "LocalSend Kotlin"
        const val GALLERY_FOLDER = "LocalSend Kotlin"
        const val RELATIVE_PATH = "Download/$FOLDER_NAME/"
    }
}
