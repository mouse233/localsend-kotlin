package io.github.mouse233.localsendkotlin.transfer

import android.content.Context
import io.github.mouse233.localsendkotlin.model.DeviceInfo
import io.github.mouse233.localsendkotlin.model.ReceivedFile
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Stores incoming LocalSend uploads without buffering entire files in memory. */
class IncomingTransferManager(
    context: Context,
    private val onTransferRequested: (PrepareUploadRequest, (Boolean) -> Unit) -> Unit,
    private val onTransferCancelRequested: (DeviceInfo, String, String) -> Unit,
    private val onFileProgress: (String, Long, Long) -> Unit,
    private val onFileReceiveCancelled: (String) -> Unit,
    private val onFileReceived: (ReceivedFile) -> Unit
) {
    private val fileStore = IncomingFileStore(context)
    private val sessions = ConcurrentHashMap<String, Session>()

    fun prepare(request: PrepareUploadRequest, remoteAddress: String): PrepareUploadResponse? {
        val decisionLatch = CountDownLatch(1)
        val decisionReceived = AtomicBoolean(false)
        val accepted = AtomicBoolean(false)
        onTransferRequested(request) { decision ->
            if (decisionReceived.compareAndSet(false, true)) {
                accepted.set(decision)
                decisionLatch.countDown()
            }
        }
        if (!decisionLatch.await(DECISION_TIMEOUT_SECONDS, TimeUnit.SECONDS) || !accepted.get()) return null

        val sessionId = UUID.randomUUID().toString()
        val targets = request.files.mapValues { (_, file) ->
            val token = UUID.randomUUID().toString()
            Target(token, file, fileStore.create(file.fileName, file.fileType, file.size))
        }
        sessions[sessionId] = Session(targets, request.info, remoteAddress)
        activeSessionId = sessionId
        return PrepareUploadResponse(sessionId, targets.mapValues { it.value.token })
    }

    fun write(
        sessionId: String,
        fileId: String,
        token: String,
        input: InputStream,
        contentLength: Long?,
        isChunked: Boolean
    ): Boolean {
        val target = sessions[sessionId]?.files?.get(fileId) ?: return false
        if (target.token != token || contentLength != null && contentLength != target.file.size) return false
        val digest = MessageDigest.getInstance("SHA-256")
        val source = if (isChunked) ChunkedInputStream(input) else input
        try {
            fileStore.withOutput(target.destination) { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var received = 0L
                if (isChunked) {
                    // Chunked bodies have no Content-Length. Read through the terminal chunk.
                    while (true) {
                        if (!isSessionActive(sessionId, fileId, target)) return discardAsCancelled(target)
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (received + count > target.file.size) {
                            fileStore.discard(target.destination)
                            return false
                        }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        received += count
                        onFileProgress(target.file.fileName, received, target.file.size)
                    }
                } else {
                    // A keep-alive HTTP connection does not close after the request body.
                    // For a Content-Length request, stop exactly at the declared file size.
                    while (received < target.file.size) {
                        if (!isSessionActive(sessionId, fileId, target)) return discardAsCancelled(target)
                        val count = source.read(buffer, 0, minOf(buffer.size.toLong(), target.file.size - received).toInt())
                        if (count < 0) {
                            return discardAsCancelled(target)
                        }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        received += count
                        onFileProgress(target.file.fileName, received, target.file.size)
                    }
                }
                if (received != target.file.size) {
                    fileStore.discard(target.destination)
                    return false
                }
            }
        } catch (_: Exception) {
            return discardAsCancelled(target)
        }
        if (!isSessionActive(sessionId, fileId, target)) return discardAsCancelled(target)
        if (target.file.sha256 != null && !target.file.sha256.equals(digest.digest().joinToString("") { "%02x".format(it) }, true)) {
            fileStore.discard(target.destination); return false
        }
        fileStore.complete(target.destination)
        target.completed = true
        if (sessions[sessionId]?.files?.values?.all { it.completed } == true) sessions.remove(sessionId)
        if (sessions[sessionId] == null) activeSessionId = null
        onFileReceived(target.destination.file)
        return true
    }

    fun cancel(sessionId: String) {
        sessions.remove(sessionId)?.files?.values
            ?.filterNot { it.completed }
            ?.forEach { fileStore.discard(it.destination) }
        if (activeSessionId == sessionId) activeSessionId = null
    }

    fun cancelCurrent(): Boolean {
        val sessionId = activeSessionId ?: return false
        val session = sessions.remove(sessionId) ?: return false
        activeSessionId = null
        session.files.values.filterNot { it.completed }.forEach { fileStore.discard(it.destination) }
        onTransferCancelRequested(session.info, session.remoteAddress, sessionId)
        session.files.values.firstOrNull { !it.completed }?.let { onFileReceiveCancelled(it.file.fileName) }
        return true
    }

    private fun isSessionActive(sessionId: String, fileId: String, target: Target): Boolean =
        sessions[sessionId]?.files?.get(fileId) === target

    private fun discardAsCancelled(target: Target): Boolean {
        fileStore.discard(target.destination)
        onFileReceiveCancelled(target.file.fileName)
        return false
    }

    data class PrepareUploadRequest(val info: DeviceInfo, val files: Map<String, IncomingFile>)
    data class IncomingFile(val id: String, val fileName: String, val size: Long, val fileType: String, val sha256: String?)
    data class PrepareUploadResponse(val sessionId: String, val files: Map<String, String>)
    @Volatile private var activeSessionId: String? = null
    private data class Session(val files: Map<String, Target>, val info: DeviceInfo, val remoteAddress: String)
    private data class Target(val token: String, val file: IncomingFile, val destination: IncomingFileStore.Destination, var completed: Boolean = false)
    private companion object {
        const val BUFFER_SIZE = 32 * 1024
        const val DECISION_TIMEOUT_SECONDS = 60L
    }
}
