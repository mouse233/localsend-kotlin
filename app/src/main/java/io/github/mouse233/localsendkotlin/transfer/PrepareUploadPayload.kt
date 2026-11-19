package io.github.mouse233.localsendkotlin.transfer

import io.github.mouse233.localsendkotlin.model.DeviceInfoPayload

/**
 * prepare-upload JSON received from a sender. Like [DeviceInfoPayload], every field is
 * nullable because Gson can leave any of them null; [toRequest] validates the request
 * on the server thread before it reaches the transfer manager or the UI.
 */
data class PrepareUploadPayload(
    val info: DeviceInfoPayload?,
    val files: Map<String, IncomingFilePayload?>?
) {
    /** Returns null when the sender identity or any file entry is incomplete. */
    fun toRequest(): IncomingTransferManager.PrepareUploadRequest? {
        val device = info?.toDeviceInfo() ?: return null
        val entries = files?.takeIf { it.isNotEmpty() } ?: return null
        val validated = LinkedHashMap<String, IncomingTransferManager.IncomingFile>()
        for ((key, payload) in entries) {
            validated[key] = payload?.toIncomingFile(key) ?: return null
        }
        return IncomingTransferManager.PrepareUploadRequest(device, validated)
    }
}

data class IncomingFilePayload(
    val id: String?,
    val fileName: String?,
    val size: Long?,
    val fileType: String?,
    val sha256: String?,
    val preview: String?
) {
    /** The map key is the protocol's file id; an explicit id must agree with it. */
    fun toIncomingFile(key: String): IncomingTransferManager.IncomingFile? {
        val fileId = id ?: key
        if (fileId != key) return null
        val fileSize = size ?: return null
        if (fileSize < 0) return null
        return IncomingTransferManager.IncomingFile(
            id = fileId,
            fileName = fileName ?: return null,
            size = fileSize,
            fileType = fileType ?: return null,
            sha256 = sha256,
            preview = preview
        )
    }
}
