package io.github.mouse233.localsendkotlin.transfer

import io.github.mouse233.localsendkotlin.model.DeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingReceiveOptionsTest {
    @Test
    fun selectedFilesAndRenamesAreAppliedBeforePreparingTheSession() {
        val request = IncomingTransferManager.PrepareUploadRequest(
            DeviceInfo("sender", "2.0", null, null, "fingerprint", 53317, "https"),
            linkedMapOf(
                "photo" to IncomingTransferManager.IncomingFile("photo", "original.jpg", 10, "image/jpeg", null),
                "document" to IncomingTransferManager.IncomingFile("document", "notes.txt", 20, "text/plain", null)
            )
        )
        val options = IncomingReceiveOptions(
            selectedFileIds = setOf("photo"),
            renamedFiles = mapOf("photo" to "renamed.jpg"),
            receiveDirectoryUri = null,
            saveMediaToGallery = true
        )

        val selected = options.selectedFiles(request)
        assertEquals(setOf("photo"), selected.keys)
        assertEquals("renamed.jpg", options.displayName("photo", selected.getValue("photo").fileName))
        assertEquals("notes.txt", options.displayName("document", "notes.txt"))
        assertTrue(options.saveMediaToGallery)
    }

    @Test
    fun textPreviewIsRecognizedAsMessageInsteadOfFile() {
        val request = IncomingTransferManager.PrepareUploadRequest(
            DeviceInfo("sender", "2.0", null, null, "fingerprint", 53317, "https"),
            linkedMapOf(
                "message" to IncomingTransferManager.IncomingFile(
                    "message", "message.txt", 5, "text/plain", null, "hello"
                )
            )
        )

        assertEquals("hello", request.messageText())
    }

    @Test
    fun textFileWithoutPreviewRemainsAFile() {
        val request = IncomingTransferManager.PrepareUploadRequest(
            DeviceInfo("sender", "2.0", null, null, "fingerprint", 53317, "https"),
            linkedMapOf(
                "document" to IncomingTransferManager.IncomingFile(
                    "document", "notes.txt", 5, "text/plain", null
                )
            )
        )

        assertNull(request.messageText())
    }
}
