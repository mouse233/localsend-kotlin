package io.github.mouse233.localsendkotlin.transfer

import android.net.Uri
import io.github.mouse233.localsendkotlin.settings.AppSettings

/** One-time choices made for a single incoming transfer session. */
data class IncomingReceiveOptions(
    val selectedFileIds: Set<String>,
    val renamedFiles: Map<String, String>,
    val receiveDirectoryUri: Uri?,
    val saveMediaToGallery: Boolean
) {
    fun selectedFiles(request: IncomingTransferManager.PrepareUploadRequest): Map<String, IncomingTransferManager.IncomingFile> =
        request.files.filterKeys { it in selectedFileIds }

    fun displayName(fileId: String, originalName: String): String =
        renamedFiles[fileId]?.trim().orEmpty().ifBlank { originalName }

    companion object {
        fun forAll(
            request: IncomingTransferManager.PrepareUploadRequest,
            settings: AppSettings,
            saveMediaToGallery: Boolean = false
        ): IncomingReceiveOptions = IncomingReceiveOptions(
            selectedFileIds = request.files.keys,
            renamedFiles = emptyMap(),
            receiveDirectoryUri = settings.receiveDirectoryUri(),
            saveMediaToGallery = saveMediaToGallery
        )
    }
}
