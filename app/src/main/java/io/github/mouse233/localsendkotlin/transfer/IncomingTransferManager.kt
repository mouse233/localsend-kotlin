package io.github.mouse233.localsendkotlin.transfer

import android.content.Context
import io.github.mouse233.localsendkotlin.model.DeviceInfo
import io.github.mouse233.localsendkotlin.model.ReceivedFile
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Stores incoming LocalSend uploads without buffering entire files in memory. */
class IncomingTransferManager(
    context: Context,
    private val onFileReceived: (ReceivedFile) -> Unit
) {
    private val fileStore = IncomingFileStore(context)
    private val sessions = ConcurrentHashMap<String, Session>()

    fun prepare(request: PrepareUploadRequest): PrepareUploadResponse {
        val sessionId = UUID.randomUUID().toString()
        val targets = request.files.mapValues { (_, file) ->
            val token = UUID.randomUUID().toString()
            Target(token, file, fileStore.create(file.fileName, file.fileType, file.size))
        }
        sessions[sessionId] = Session(targets)
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
            fileStore.openOutput(target.destination).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var received = 0L
                if (isChunked) {
                    // Chunked bodies have no Content-Length. Read through the terminal chunk.
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (received + count > target.file.size) {
                            fileStore.discard(target.destination)
                            return false
                        }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        received += count
                    }
                } else {
                    // A keep-alive HTTP connection does not close after the request body.
                    // For a Content-Length request, stop exactly at the declared file size.
                    while (received < target.file.size) {
                        val count = source.read(buffer, 0, minOf(buffer.size.toLong(), target.file.size - received).toInt())
                        if (count < 0) {
                            fileStore.discard(target.destination)
                            return false
                        }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        received += count
                    }
                }
                if (received != target.file.size) {
                    fileStore.discard(target.destination)
                    return false
                }
            }
        } catch (_: Exception) {
            fileStore.discard(target.destination)
            return false
        }
        if (target.file.sha256 != null && !target.file.sha256.equals(digest.digest().joinToString("") { "%02x".format(it) }, true)) {
            fileStore.discard(target.destination); return false
        }
        fileStore.complete(target.destination)
        target.completed = true
        if (sessions[sessionId]?.files?.values?.all { it.completed } == true) sessions.remove(sessionId)
        onFileReceived(target.destination.file)
        return true
    }

    fun cancel(sessionId: String) {
        sessions.remove(sessionId)?.files?.values
            ?.filterNot { it.completed }
            ?.forEach { fileStore.discard(it.destination) }
    }

    data class PrepareUploadRequest(val info: DeviceInfo, val files: Map<String, IncomingFile>)
    data class IncomingFile(val id: String, val fileName: String, val size: Long, val fileType: String, val sha256: String?)
    data class PrepareUploadResponse(val sessionId: String, val files: Map<String, String>)
    private data class Session(val files: Map<String, Target>)
    private data class Target(val token: String, val file: IncomingFile, val destination: IncomingFileStore.Destination, var completed: Boolean = false)
    private companion object { const val BUFFER_SIZE = 32 * 1024 }
}
