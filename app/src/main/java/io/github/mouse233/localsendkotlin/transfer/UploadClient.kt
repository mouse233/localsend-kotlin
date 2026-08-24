package io.github.mouse233.localsendkotlin.transfer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.gson.Gson
import io.github.mouse233.localsendkotlin.discovery.LocalIdentity
import io.github.mouse233.localsendkotlin.model.RemoteDevice
import io.github.mouse233.localsendkotlin.protocol.LocalSendProtocol
import io.github.mouse233.localsendkotlin.settings.AppSettings
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
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val settings = AppSettings(appContext)
    private val httpClient = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val gson = Gson()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val cancelExecutor: ExecutorService = Executors.newCachedThreadPool()
    @Volatile private var activeSessionId: String? = null
    @Volatile private var activeDevice: RemoteDevice? = null
    @Volatile private var activeCancelFlag: AtomicBoolean? = null

    fun send(uri: Uri, device: RemoteDevice, listener: Listener) = send(listOf(uri), device, listener)

    fun send(uris: List<Uri>, device: RemoteDevice, listener: Listener) {
        executor.execute {
            try {
                require(uris.isNotEmpty()) { "至少选择一个文件" }
                activeDevice = device
                val cancelled = AtomicBoolean(false)
                activeCancelFlag = cancelled
                listener.onStatus("正在准备 ${uris.size} 个文件…")
                val files = uris.mapIndexed { index, uri ->
                    listener.onStatus("正在准备文件 ${index + 1}/${uris.size}…")
                    val file = readFile(uri)
                    file.copy(
                        id = UUID.randomUUID().toString(),
                        sha256 = if (settings.createChecksums()) sha256(uri) { cancelled.get() } else null
                    )
                }
                listener.onStatus("正在请求 ${device.alias} 接收文件…")
                val prepared = prepare(device, files)
                activeTransfers[prepared.sessionId] = cancelled
                activeSessionId = prepared.sessionId
                try {
                    var totalSent = 0L
                    val totalBytes = files.sumOf { it.size }
                    files.forEachIndexed { index, file ->
                        val token = prepared.files[file.id] ?: throw IllegalStateException("接收方未接受文件：${file.fileName}")
                        listener.onStatus("正在发送 ${index + 1}/${files.size}：${file.fileName}")
                        upload(device, prepared.sessionId, file.id, token, file, cancelled) { sent ->
                            listener.onProgress(file.fileName, index, files.size, sent, file.size, totalSent + sent, totalBytes)
                        }
                        totalSent += file.size
                    }
                } finally {
                    activeTransfers.remove(prepared.sessionId, cancelled)
                    activeSessionId = null
                    activeCancelFlag = null
                    activeDevice = null
                }
                listener.onCompleted(files.map { it.fileName })
            } catch (exception: Exception) {
                activeCancelFlag = null
                activeSessionId = null
                activeDevice = null
                listener.onError(exception.message ?: "文件发送失败")
            }
        }
    }

    fun cancelCurrent() {
        activeCancelFlag?.set(true)
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

    private fun prepare(device: RemoteDevice, files: List<FileInfo>): PrepareResponse {
        val request = Request.Builder()
            .url(url(device, LocalSendProtocol.PREPARE_UPLOAD_PATH))
            .post(RequestBody.create(JSON, gson.toJson(PrepareRequest(identity.deviceInfo(), files.associateBy { it.id }))))
            .build()
        client(device).newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("接收方拒绝请求（${response.code()}）")
            return gson.fromJson(response.body()?.charStream(), PrepareResponse::class.java)
                ?: throw IllegalStateException("接收方响应无效")
        }
    }

    private fun upload(device: RemoteDevice, sessionId: String, fileId: String, token: String, file: FileInfo, cancelled: AtomicBoolean, onProgress: (Long) -> Unit) {
        val uploadUrl = url(device, LocalSendProtocol.UPLOAD_PATH).newBuilder()
            .addQueryParameter("sessionId", sessionId).addQueryParameter("fileId", fileId).addQueryParameter("token", token).build()
        val body = StreamBody(
            file.fileType,
            file.size,
            source = { resolver.openInputStream(file.uri) ?: throw IllegalStateException("无法读取文件") },
            progress = onProgress,
            shouldCancel = { cancelled.get() }
        )
        val request = Request.Builder().url(uploadUrl).post(body).build()
        client(device).newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("上传失败（${response.code()}）")
        }
    }

    private fun client(device: RemoteDevice): OkHttpClient = if (device.protocol == "http") httpClient else identity.tlsIdentity.createSslContext(device.fingerprint).let { context ->
        OkHttpClient.Builder().sslSocketFactory(context.socketFactory, identity.tlsIdentity.trustManagerFor(device.fingerprint))
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

    private fun sha256(uri: Uri, shouldCancel: () -> Boolean): String {
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                if (shouldCancel()) throw IOException("发送已取消")
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        } ?: throw IllegalStateException("无法读取文件")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    interface Listener {
        fun onStatus(message: String)
        fun onProgress(fileName: String, fileIndex: Int, fileCount: Int, sent: Long, total: Long, totalSent: Long, totalBytes: Long)
        fun onCompleted(names: List<String>)
        fun onError(message: String)
    }
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
