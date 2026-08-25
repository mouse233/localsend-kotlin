package io.github.mouse233.localsendkotlin.discovery

import android.util.Log
import com.google.gson.Gson
import io.github.mouse233.localsendkotlin.model.RegisterResponse
import io.github.mouse233.localsendkotlin.model.RemoteDevice
import io.github.mouse233.localsendkotlin.protocol.LocalSendProtocol
import io.github.mouse233.localsendkotlin.security.TlsIdentity
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.net.InetSocketAddress
import javax.net.ssl.SSLSocket
import javax.net.ssl.HostnameVerifier

/** Resolves a manually entered host and port into a LocalSend peer. */
class ManualDeviceConnector(
    private val context: android.content.Context,
    private val identity: LocalIdentity = LocalIdentity(context)
) {
    private val gson = Gson()
    private val settings = io.github.mouse233.localsendkotlin.settings.AppSettings(context)
    private val sslContext = identity.tlsIdentity.createSslContext()
    private val httpClient = createHttpClient()
    private val httpsClient = createHttpsClient()

    fun resolve(endpoint: ManualEndpoint): RemoteDevice {
        val protocols = if (settings.encryptionEnabled()) {
            listOf("https", "http")
        } else {
            listOf("http", "https")
        }
        var lastFailure: Exception? = null
        protocols.forEach { protocol ->
            try {
                return register(endpoint, protocol)
            } catch (exception: Exception) {
                Log.w(TAG, "Manual LocalSend registration failed for ${endpoint.host}:${endpoint.port} via $protocol", exception)
                lastFailure = exception
            }
        }
        throw IllegalStateException(
            "${endpoint.host}:${endpoint.port} 不是可用的 LocalSend 设备",
            lastFailure
        )
    }

    private fun register(endpoint: ManualEndpoint, protocol: String): RemoteDevice {
        val request = Request.Builder()
            .url(endpoint.url(protocol, LocalSendProtocol.REGISTER_PATH))
            .post(gson.toJson(identity.deviceInfo()).toRequestBody(JSON))
            .build()
        val client = if (protocol == "https") httpsClient else httpClient
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("注册请求失败（${response.code}）")
            val peerFingerprint = if (protocol == "https") {
                response.handshake?.peerCertificates?.firstOrNull()?.let(TlsIdentity::certificateFingerprint)
                    ?: readPeerFingerprint(endpoint)
            } else null
            val body = gson.fromJson(response.body?.charStream(), RegisterResponse::class.java)
                ?: throw IllegalStateException("设备响应无效")
            val version = body.version ?: throw IllegalStateException("设备版本未知")
            if (version.substringBefore('.') != LocalSendProtocol.VERSION.substringBefore('.')) {
                throw IllegalStateException("不兼容的 LocalSend 版本：$version")
            }
            val fingerprint = if (protocol == "https") {
                peerFingerprint
                    ?: throw IllegalStateException("无法读取设备证书指纹")
            } else {
                body.fingerprint.orEmpty()
            }
            return RemoteDevice(
                alias = body.alias?.takeIf { it.isNotBlank() } ?: endpoint.host,
                deviceModel = body.deviceModel,
                deviceType = body.deviceType,
                fingerprint = fingerprint,
                address = endpoint.host,
                port = endpoint.port,
                protocol = protocol,
                downloadEnabled = body.download
            )
        }
    }

    private fun createHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private fun createHttpsClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .followRedirects(false)
            .sslSocketFactory(sslContext.socketFactory, identity.tlsIdentity.trustManagerFor(null))
            .hostnameVerifier(HostnameVerifier { _, _ -> true })
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * OkHttp should expose the TLS handshake on an HTTPS response, but some Android/OkHttp
     * combinations return a null response.handshake after a self-signed TLS exchange. Perform
     * an explicit handshake as a compatibility fallback so the peer can still be pinned for
     * the subsequent upload.
     */
    private fun readPeerFingerprint(endpoint: ManualEndpoint): String {
        val socket = sslContext.socketFactory.createSocket() as SSLSocket
        try {
            socket.soTimeout = TIMEOUT_MILLIS.toInt()
            socket.connect(InetSocketAddress(endpoint.host, endpoint.port), TIMEOUT_MILLIS.toInt())
            socket.startHandshake()
            val certificate = socket.session.peerCertificates.firstOrNull()
                ?: throw IllegalStateException("设备未提供 TLS 证书")
            return TlsIdentity.certificateFingerprint(certificate)
        } finally {
            socket.close()
        }
    }

    private companion object {
        const val TAG = "ManualDeviceConnector"
        const val TIMEOUT_SECONDS = 5L
        const val TIMEOUT_MILLIS = TIMEOUT_SECONDS * 1000L
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

data class ManualEndpoint(val host: String, val port: Int) {
    fun matches(address: String, otherPort: Int): Boolean =
        port == otherPort && normalizedHost(host).equals(normalizedHost(address), ignoreCase = true)

    fun url(protocol: String, path: String): String {
        val formattedHost = if (host.contains(':')) "[$host]" else host
        return "$protocol://$formattedHost:$port$path"
    }

    companion object {
        fun parse(value: String): ManualEndpoint {
            val input = value.trim()
            require(input.isNotEmpty()) { "请输入 IP 地址和端口" }
            val host: String
            val portText: String
            if (input.startsWith('[')) {
                val closingBracket = input.indexOf(']')
                require(closingBracket > 1 && input.substring(closingBracket + 1).startsWith(':')) {
                    "IPv6 地址必须使用 [地址]:端口 格式"
                }
                host = input.substring(1, closingBracket)
                portText = input.substring(closingBracket + 2)
            } else {
                val separator = input.lastIndexOf(':')
                require(separator > 0 && separator == input.indexOf(':')) {
                    "请输入 IP:端口，IPv6 请使用 [地址]:端口 格式"
                }
                host = input.substring(0, separator)
                portText = input.substring(separator + 1)
            }
            require(host.isNotBlank() && !host.any { it.isWhitespace() || it == '/' }) { "IP 地址无效" }
            val port = portText.toIntOrNull() ?: throw IllegalArgumentException("端口必须是数字")
            require(port in 1..65535) { "端口必须在 1 到 65535 之间" }
            return ManualEndpoint(host, port)
        }

        private fun normalizedHost(value: String): String =
            value.trim().removePrefix("[").removeSuffix("]")
    }
}
