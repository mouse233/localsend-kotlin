package io.github.mouse233.localsendkotlin.server

import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import io.github.mouse233.localsendkotlin.model.DeviceInfo
import io.github.mouse233.localsendkotlin.protocol.LocalSendProtocol
import io.github.mouse233.localsendkotlin.security.TlsIdentity
import java.net.ServerSocket
import java.net.Socket
import java.io.IOException
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/** Minimal HTTP server needed for LocalSend's two-way device discovery. */
class LocalSendServer(
    private val gson: Gson,
    tlsIdentity: TlsIdentity,
    private val localDevice: () -> DeviceInfo,
    private val onDeviceRegistered: (DeviceInfo, String) -> Unit
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
        if (session.method != Method.POST || session.uri != LocalSendProtocol.REGISTER_PATH) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }

        return try {
            val device = gson.fromJson(readUtf8Body(session), DeviceInfo::class.java)
                ?: return badRequest()
            if (device.fingerprint.isBlank() || device.port !in 1..65535) {
                return badRequest()
            }
            if (!device.fingerprint.equals(clientFingerprint.get(), ignoreCase = true)) {
                return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Invalid certificate")
            }

            onDeviceRegistered(device, session.remoteIpAddress)
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json; charset=utf-8",
                gson.toJson(localDevice())
            )
        } catch (exception: Exception) {
            badRequest()
        }
    }

    private fun badRequest(): Response =
        newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Invalid body")

    override fun createClientHandler(socket: Socket, inputStream: java.io.InputStream): ClientHandler {
        if (socket is SSLSocket) {
            socket.addHandshakeCompletedListener { event ->
                clientFingerprint.set(TlsIdentity.certificateFingerprint(event.session.peerCertificates.first()))
            }
        }
        return super.createClientHandler(socket, inputStream)
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
        const val MAX_REGISTER_BODY_BYTES = 64 * 1024
    }
}
