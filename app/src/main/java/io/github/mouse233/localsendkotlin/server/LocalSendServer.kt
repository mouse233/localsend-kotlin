package io.github.mouse233.localsendkotlin.server

import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import io.github.mouse233.localsendkotlin.model.DeviceInfo
import io.github.mouse233.localsendkotlin.protocol.LocalSendProtocol
import io.github.mouse233.localsendkotlin.security.TlsIdentity
import io.github.mouse233.localsendkotlin.transfer.IncomingTransferManager
import java.net.ServerSocket
import java.net.Socket
import java.io.IOException
import java.nio.charset.StandardCharsets
import android.util.Log
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/** Minimal HTTP server needed for LocalSend's two-way device discovery. */
class LocalSendServer(
    private val gson: Gson,
    tlsIdentity: TlsIdentity,
    private val localDevice: () -> DeviceInfo,
    private val onDeviceRegistered: (DeviceInfo, String) -> Unit,
    private val incomingTransfers: IncomingTransferManager,
    private val onTransferCancelled: (String) -> Unit = {}
) : NanoHTTPD(LocalSendProtocol.DEFAULT_PORT) {

    private val clientFingerprint = ThreadLocal<String?>()

    init {
        val socketFactory = tlsIdentity.createSslContext().serverSocketFactory
        setServerSocketFactory(object : ServerSocketFactory {
            override fun create(): ServerSocket = (socketFactory.createServerSocket() as SSLServerSocket).apply {
                needClientAuth = true
            }
        })
    }

    override fun serve(session: IHTTPSession): Response {
        val contentLengthHeader = session.headers["content-length"]
        val transferEncodingHeader = session.headers["transfer-encoding"]
        Log.i(
            TAG,
            "Request method=${session.method} uri=${session.uri} remote=${session.remoteIpAddress} " +
                "contentLength=$contentLengthHeader transferEncoding=$transferEncodingHeader " +
                "parameters=${session.parms.keys}"
        )
        if (session.method != Method.POST) {
            Log.w(TAG, "Rejecting non-POST request")
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }

        return try {
            if (session.uri == LocalSendProtocol.PREPARE_UPLOAD_PATH) return prepareUpload(session)
            if (session.uri == LocalSendProtocol.UPLOAD_PATH) return receiveFile(session)
            if (session.uri == LocalSendProtocol.CANCEL_PATH) return cancel(session)
            if (session.uri != LocalSendProtocol.REGISTER_PATH) {
                Log.w(TAG, "Rejecting unknown route: ${session.uri}")
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
            val device = gson.fromJson(readUtf8Body(session), DeviceInfo::class.java)
                ?: return badRequest()
            if (!validIdentity(device)) return forbidden()

            onDeviceRegistered(device, session.remoteIpAddress)
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json; charset=utf-8",
                gson.toJson(localDevice())
            )
        } catch (exception: Exception) {
            Log.e(TAG, "LocalSend request failed: ${session.uri}", exception)
            badRequest()
        }
    }

    private fun badRequest(reason: String = "Invalid body"): Response {
        Log.w(TAG, "Bad request: $reason")
        return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, reason)
    }
    private fun forbidden(): Response = newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Invalid certificate")
    private fun rejected(): Response = newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Rejected")

    private fun prepareUpload(session: IHTTPSession): Response {
        val request = gson.fromJson(readUtf8Body(session), IncomingTransferManager.PrepareUploadRequest::class.java) ?: return badRequest()
        Log.i(TAG, "Prepare upload: files=${request.files.size} ids=${request.files.keys}")
        if (!validIdentity(request.info)) {
            Log.w(TAG, "Prepare upload rejected because client identity did not match TLS certificate")
            return forbidden()
        }
        val response = incomingTransfers.prepare(request, session.remoteIpAddress) ?: return rejected()
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", gson.toJson(response))
    }

    private fun receiveFile(session: IHTTPSession): Response {
        val sessionId = session.parms["sessionId"] ?: return badRequest("Missing sessionId")
        val fileId = session.parms["fileId"] ?: return badRequest("Missing fileId")
        val token = session.parms["token"] ?: return badRequest("Missing token")
        val isChunked = session.headers["transfer-encoding"]
            ?.split(',')
            ?.any { it.trim().equals("chunked", true) } == true
        val contentLength = session.headers["content-length"]?.toLongOrNull()
        if (!isChunked && contentLength == null) return badRequest("Missing Content-Length or chunked encoding")
        Log.i(TAG, "Receiving file: session=$sessionId file=$fileId bytes=$contentLength chunked=$isChunked")
        return if (incomingTransfers.write(sessionId, fileId, token, session.inputStream, contentLength, isChunked)) {
            Log.i(TAG, "Upload completed: session=$sessionId file=$fileId")
            // LocalSend treats an empty 200 response as a completed upload. 204 is reserved
            // for prepare-upload when there is no file to transfer.
            newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
        }
        else {
            Log.w(TAG, "Upload rejected: unknown session/file, invalid token, truncated body, or checksum mismatch")
            newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Invalid upload")
        }
    }

    private fun cancel(session: IHTTPSession): Response {
        val sessionId = session.parms["sessionId"] ?: return badRequest(); incomingTransfers.cancel(sessionId)
        onTransferCancelled(sessionId)
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
    }

    private fun validIdentity(device: DeviceInfo): Boolean = device.fingerprint.isNotBlank() && device.port in 1..65535 && device.fingerprint.equals(clientFingerprint.get(), true)

    override fun createClientHandler(socket: Socket, inputStream: java.io.InputStream): ClientHandler {
        return object : ClientHandler(inputStream, socket) {
            override fun run() {
                try {
                    if (socket is SSLSocket) {
                        // NanoHTTPD 2.3.1 在 accept 线程先调用 socket.getInputStream() 再注册
                        // handshakeCompletedListener；Android 5.1 的 SSLSocket 会在 getInputStream()
                        // 时同步完成 TLS 握手，导致监听器永远不会触发、ThreadLocal 指纹恒为 null。
                        // 改为在本请求线程主动握手（幂等）后直接读取对端证书计算指纹。
                        socket.startHandshake()
                        val certificate = socket.session.peerCertificates.firstOrNull()
                            ?: throw IllegalStateException("Peer did not present a certificate")
                        clientFingerprint.set(TlsIdentity.certificateFingerprint(certificate))
                        Log.i(TAG, "TLS peer fingerprint: ${clientFingerprint.get()}")
                    }
                } catch (exception: Exception) {
                    Log.w(TAG, "TLS handshake failed: ${exception.message}")
                }
                super.run()
            }
        }
    }

    /** NanoHTTPD 2.3.1 defaults JSON without charset to US-ASCII; protocol JSON is UTF-8. */
    private fun readUtf8Body(session: IHTTPSession): String {
        val length = session.headers["content-length"]?.toIntOrNull()
            ?: throw IOException("Missing content length")
        if (length < 0 || length > MAX_REGISTER_BODY_BYTES) {
            throw IOException("Invalid content length")
        }
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = session.inputStream.read(bytes, offset, length - offset)
            if (count < 0) throw IOException("Unexpected end of request body")
            offset += count
        }
        return String(bytes, StandardCharsets.UTF_8).trim()
    }

    private companion object {
        const val TAG = "LocalSendServer"
        const val MAX_REGISTER_BODY_BYTES = 64 * 1024
    }
}
