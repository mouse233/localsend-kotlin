package io.github.mouse233.localsendkotlin.discovery

import io.github.mouse233.localsendkotlin.model.RemoteDevice
import io.github.mouse233.localsendkotlin.model.ReceivedFile
import io.github.mouse233.localsendkotlin.transfer.IncomingTransferManager

/** Boundary between UDP/HTTP discovery and the UI layer. */
interface DiscoveryListener {
    fun onDevicesChanged(devices: List<RemoteDevice>)
    fun onDiscoveryError(message: String)
    fun onIncomingTransferRequest(request: IncomingTransferManager.PrepareUploadRequest, decide: (Boolean) -> Unit)
    fun onIncomingSessionPrepared(sessionId: String, request: IncomingTransferManager.PrepareUploadRequest)
    fun onFileReceiveProgress(sessionId: String, fileId: String, fileName: String, received: Long, total: Long)
    fun onFileReceiveCancelled(sessionId: String, fileId: String, fileName: String, sessionComplete: Boolean)
    fun onFileReceived(sessionId: String, fileId: String, file: ReceivedFile, sessionComplete: Boolean)
}
