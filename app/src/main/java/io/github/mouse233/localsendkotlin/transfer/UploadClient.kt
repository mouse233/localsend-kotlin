package io.github.mouse233.localsendkotlin.transfer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.gson.Gson
import io.github.mouse233.localsendkotlin.discovery.LocalIdentity
import io.github.mouse233.localsendkotlin.model.RemoteDevice
import io.github.mouse233.localsendkotlin.protocol.LocalSendProtocol
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.security.MessageDigest
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class UploadClient(context: Context, private val identity: LocalIdentity) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver
    private val gson = Gson()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val cancelExecutor: ExecutorService = Executors.newCachedThreadPool()
    @Volatile private var activeSessionId: String? = null
    @Volatile private var activeDevice: RemoteDevice? = null

    fun send(uri: Uri, device: RemoteDevice, listener: Listener) {
        executor.execute {
            try {
                val file = readFile(uri)
                listener.onStatus("正在计算文件校验值…")
                val fileId = UUID.randomUUID().toString()
                val sha256 = sha256(uri)
                listener.onStatus("正在请求 ${device.alias} 接收文件…")
                val prepared = prepare(device, file.copy(id = fileId, sha256 = sha256))
                val token = prepared.files[fileId] ?: throw IllegalStateException("接收方未接受该文件")
                val cancelled = AtomicBoolean(false)
                activeTransfers[prepared.sessionId] = cancelled
                activeSessionId = prepared.sessionId
                activeDevice = device
                try {
                    upload(device, prepared.sessionId, fileId, token, file, listener, cancelled)
                } finally {
                    activeTransfers.remove(prepared.sessionId, cancelled)
                    activeSessionId = null
                    activeDevice = null
                }
                listener.onCompleted(file.fileName)
            } catch (exception: Exception) {
                listener.onError(exception.message ?: "文件发送失败")
            }
        }
    }

    fun cancelCurrent() {
        val sessionId = activeSessionId ?: return
        val device = activeDevice
        cancel(sessionId)
        if (device != null) {
            cancelExecutor.execute {
                try {
                    val request = Request.Builder()
                        .url(url(device, LocalSendProtocol.CANCEL_PATH).newBuilder().addQueryParameter("sessionId", sessionId).build())
                        .post(RequestBody.create(null, ByteArray(0)))
                        .build()
                    client(device).newCall(request).execute().use { }
                } catch (_: Exception) {
                    // The local cancellation flag is sufficient if the peer is already disconnected.
                }
            }
        }
    }

    private fun prepare(device: RemoteDevice, file: FileInfo): PrepareResponse {
        val request = Request.Builder()
            .url(url(device, LocalSendProtocol.PREPARE_UPLOAD_PATH))
            .post(RequestBody.create(JSON, gson.toJson(PrepareRequest(identity.deviceInfo(), mapOf(file.id to file)))))
            .build()
        client(device).newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("接收方拒绝请求（${response.code()}）")
            return gson.fromJson(response.body()?.charStream(), PrepareResponse::class.java)
                ?: throw IllegalStateException("接收方响应无效")
        }
    }

    private fun upload(device: RemoteDevice, sessionId: String, fileId: String, token: String, file: FileInfo, listener: Listener, cancelled: AtomicBoolean) {
        val uploadUrl = url(device, LocalSendProtocol.UPLOAD_PATH).newBuilder()
            .addQueryParameter("sessionId", sessionId).addQueryParameter("fileId", fileId).addQueryParameter("token", token).build()
        val body = StreamBody(
            file.fileType,
            file.size,
            source = { resolver.openInputStream(file.uri) ?: throw IllegalStateException("无法读取文件") },
            progress = { sent -> listener.onProgress(sent, file.size) },
            shouldCancel = { cancelled.get() }
        )
        val request = Request.Builder().url(uploadUrl).post(body).build()
        client(device).newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("上传失败（${response.code()}）")
        }
    }

    private fun client(device: RemoteDevice): OkHttpClient = identity.tlsIdentity.createSslContext().let { context ->
        OkHttpClient.Builder().sslSocketFactory(context.socketFactory, identity.tlsIdentity.trustManager)
            .hostnameVerifier { _, _ -> true }.connectTimeout(8, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    }

    private fun url(device: RemoteDevice, path: String): HttpUrl = HttpUrl.parse("${device.protocol}://${device.address}:${device.port}$path")
        ?: throw IllegalArgumentException("无效的设备地址")

    private fun readFile(uri: Uri): FileInfo {
        var name = "shared-file"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) { name = cursor.getString(0) ?: name; size = cursor.getLong(1) }
        }
        if (size < 0) throw IllegalArgumentException("无法确定文件大小")
        return FileInfo("", uri, name, size, resolver.getType(uri) ?: "application/octet-stream", null)
    }

    private fun sha256(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(BUFFER_SIZE); while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        } ?: throw IllegalStateException("无法读取文件")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    interface Listener { fun onStatus(message: String); fun onProgress(sent: Long, total: Long); fun onCompleted(name: String); fun onError(message: String) }
    private data class PrepareRequest(val info: Any, val files: Map<String, FileInfo>)
    private data class PrepareResponse(val sessionId: String, val files: Map<String, String>)
    private data class FileInfo(val id: String, val uri: Uri, val fileName: String, val size: Long, val fileType: String, val sha256: String?)
    private class StreamBody(private val type: String, private val length: Long, private val source: () -> java.io.InputStream, private val progress: (Long) -> Unit, private val shouldCancel: () -> Boolean) : RequestBody() {
        override fun contentType(): MediaType? = MediaType.parse(type)
        override fun contentLength(): Long = length
        override fun writeTo(sink: BufferedSink) { source().use { input -> val buffer = ByteArray(BUFFER_SIZE); var sent = 0L; while (true) { if (shouldCancel()) throw IOException("发送已取消"); val count = input.read(buffer); if (count < 0) break; sink.write(buffer, 0, count); sent += count; progress(sent) } } }
    }
    companion object {
        private val activeTransfers = ConcurrentHashMap<String, AtomicBoolean>()
        fun cancel(sessionId: String): Boolean = activeTransfers[sessionId]?.let { it.set(true); true } == true
        private val JSON = MediaType.parse("application/json; charset=utf-8")!!
        private const val BUFFER_SIZE = 32 * 1024
    }
}
