package io.github.mouse233.localsendkotlin.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import javax.net.ssl.HostnameVerifier
import io.github.mouse233.localsendkotlin.model.DeviceInfo
import io.github.mouse233.localsendkotlin.model.RegisterResponse
import io.github.mouse233.localsendkotlin.model.RemoteDevice
import io.github.mouse233.localsendkotlin.protocol.LocalSendProtocol
import io.github.mouse233.localsendkotlin.server.LocalSendServer
import io.github.mouse233.localsendkotlin.security.TlsIdentity
import io.github.mouse233.localsendkotlin.transfer.IncomingTransferManager
import io.github.mouse233.localsendkotlin.transfer.UploadClient
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Foreground implementation of LocalSend v2 UDP multicast discovery. */
class DiscoveryManager(
    context: Context,
    private val listener: DiscoveryListener
) {

    private val appContext = context.applicationContext
    private val gson = Gson()
    private val identity = LocalIdentity(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val devices = ConcurrentHashMap<String, RemoteDevice>()
    private val running = AtomicBoolean(false)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()
    private fun createHttpsClient(expectedFingerprint: String? = null): OkHttpClient = identity.tlsIdentity.createSslContext(expectedFingerprint).let { sslContext ->
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, identity.tlsIdentity.trustManagerFor(expectedFingerprint))
            .hostnameVerifier(HostnameVerifier { _, _ -> true })
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    @Volatile private var executor: ExecutorService? = null
    @Volatile private var legacyScanExecutor: ExecutorService? = null
    @Volatile private var socket: MulticastSocket? = null
    @Volatile private var server: LocalSendServer? = null
    @Volatile private var incomingTransfers: IncomingTransferManager? = null
    @Volatile private var multicastLock: WifiManager.MulticastLock? = null
    @Volatile private var legacyProcessNetworkBound = false

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val worker = Executors.newFixedThreadPool(2)
        executor = worker
        legacyScanExecutor = Executors.newFixedThreadPool(LEGACY_SCAN_PARALLELISM)
        worker.execute { runDiscovery() }
    }

    fun announce() {
        executor?.execute {
            if (running.get()) sendAnnouncement(announce = true)
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        socket?.close()
        socket = null
        if (legacyProcessNetworkBound) {
            // setProcessDefaultNetwork is the API 21-compatible counterpart to socket binding.
            ConnectivityManager.setProcessDefaultNetwork(null)
            legacyProcessNetworkBound = false
        }
        server?.stop()
        server = null
        multicastLock?.let { lock -> if (lock.isHeld) lock.release() }
        multicastLock = null
        executor?.shutdownNow()
        executor = null
        legacyScanExecutor?.shutdownNow()
        legacyScanExecutor = null
    }

    private fun runDiscovery() {
        try {
            acquireMulticastLock()
            val transferManager = IncomingTransferManager(
                appContext,
                onTransferRequested = { request, decide ->
                    mainHandler.post { listener.onIncomingTransferRequest(request, decide) }
                },
                onTransferCancelRequested = { info, address, sessionId ->
                    cancelRemoteTransfer(info.protocol, address, info.port, info.fingerprint, sessionId)
                },
                onFileProgress = { fileName, received, total ->
                    mainHandler.post { listener.onFileReceiveProgress(fileName, received, total) }
                },
                onFileReceiveCancelled = { fileName ->
                    mainHandler.post { listener.onFileReceiveCancelled(fileName) }
                },
                onFileReceived = { file ->
                    mainHandler.post { listener.onFileReceived(file) }
                }
            )
            incomingTransfers = transferManager
            val localServer = LocalSendServer(
                gson,
                identity.tlsIdentity,
                identity::deviceInfo,
                ::registerDevice,
                transferManager,
                onTransferCancelled = UploadClient::cancel
            )
            localServer.start(SOCKET_READ_TIMEOUT_MS, false)
            server = localServer

            val multicastSocket = createMulticastSocket()
            socket = multicastSocket
            Log.i(TAG, "Listening for LocalSend discovery on ${multicastSocket.networkInterface.name}")
            sendAnnouncement(announce = true)
            scheduleMulticastRetries()
            scheduleLegacyScan()
            receiveAnnouncements(multicastSocket)
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to start LocalSend discovery", exception)
            reportError("Unable to start discovery: ${exception.message ?: "network error"}")
        } finally {
            stop()
        }
    }

    fun cancelIncomingTransfer(): Boolean = incomingTransfers?.cancelCurrent() == true

    private fun cancelRemoteTransfer(protocol: String, address: String, port: Int, fingerprint: String, sessionId: String) {
        try {
            val url = "$protocol://$address:$port${LocalSendProtocol.CANCEL_PATH}?sessionId=$sessionId"
            val request = Request.Builder().url(url).post(RequestBody.create(null, ByteArray(0))).build()
            (if (protocol == "https") createHttpsClient(fingerprint) else httpClient).newCall(request).execute().use { }
        } catch (_: Exception) { }
    }

    private fun receiveAnnouncements(multicastSocket: MulticastSocket) {
        val buffer = ByteArray(MAX_PACKET_SIZE)
        while (running.get()) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                multicastSocket.receive(packet)
                val message = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                val device = gson.fromJson(message, DeviceInfo::class.java) ?: continue
                processAnnouncement(device, packet.address.hostAddress ?: continue)
            } catch (exception: IOException) {
                if (running.get()) reportError("Discovery connection lost")
                return
            } catch (exception: Exception) {
                // Ignore malformed packets from unrelated services on the multicast address.
            }
        }
    }

    private fun processAnnouncement(device: DeviceInfo, address: String) {
        if (!isCompatible(device) || device.fingerprint == identity.deviceInfo().fingerprint) return
        registerDevice(device, address)
        if (device.announce == true && !sendRegisterRequest(device, address)) {
            sendAnnouncement(announce = false)
        }
    }

    private fun sendRegisterRequest(device: DeviceInfo, address: String): Boolean {
        return postRegistration(address, device.port, device.protocol, device.fingerprint) != null
    }

    private fun postRegistration(address: String, port: Int, protocol: String, expectedFingerprint: String? = null): RegistrationResult? {
        val requestBody = RequestBody.create(JSON_MEDIA_TYPE, gson.toJson(identity.deviceInfo()))
        val request = Request.Builder()
            .url("$protocol://$address:$port${LocalSendProtocol.REGISTER_PATH}")
            .post(requestBody)
            .build()
        return try {
            val client = if (protocol == "https") createHttpsClient(expectedFingerprint) else httpClient
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val peerFingerprint = if (protocol == "https") {
                    response.handshake()?.peerCertificates()?.firstOrNull()?.let(TlsIdentity::certificateFingerprint)
                } else null
                if (expectedFingerprint != null && !expectedFingerprint.equals(peerFingerprint, ignoreCase = true)) return null
                RegistrationResult(gson.fromJson(response.body()?.charStream(), RegisterResponse::class.java), peerFingerprint)
            }
        } catch (exception: Exception) {
            null
        }
    }

    private fun scheduleLegacyScan() {
        legacyScanExecutor?.execute {
            try {
                Thread.sleep(LEGACY_SCAN_DELAY_MS)
                if (running.get() && devices.isEmpty()) scanLocalNetwork()
            } catch (exception: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    /**
     * Match LocalSend's startup announcement burst: immediately (at startup), then after
     * roughly 500 ms and 2 s. This avoids missing peers that are still joining the group.
     */
    private fun scheduleMulticastRetries() {
        executor?.execute {
            for (delay in MULTICAST_RETRY_DELAYS_MS) {
                try {
                    Thread.sleep(delay)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@execute
                }
                if (!running.get() || devices.isNotEmpty()) return@execute
                Log.d(TAG, "Retrying LocalSend multicast announcement")
                sendAnnouncement(announce = true)
            }
        }
    }

    /** HTTP legacy-mode fallback for WLANs that suppress multicast packets. */
    private fun scanLocalNetwork() {
        val transport = try {
            findWifiTransport()
        } catch (exception: IOException) {
            reportError(exception.message ?: "Unable to scan the local network")
            return
        }
        val effectivePrefix = maxOf(transport.prefixLength, MINIMUM_SCAN_PREFIX)
        val hostBits = 32 - effectivePrefix
        val networkAddress = ipv4ToInt(transport.address) and (-1 shl hostBits)
        val addressCount = 1 shl hostBits
        Log.i(TAG, "Starting HTTP legacy scan with ${addressCount - 2} hosts, nearest addresses first")
        orderedHostOffsets(networkAddress, addressCount, transport.address).forEach { offset ->
            legacyScanExecutor?.execute { scanAddress(intToIpv4(networkAddress + offset)) }
        }
    }

    /** DHCP leases are usually adjacent, so check Wi-Fi neighbours before the rest of the subnet. */
    private fun orderedHostOffsets(networkAddress: Int, addressCount: Int, localAddress: Inet4Address): List<Int> {
        val localOffset = ipv4ToInt(localAddress) - networkAddress
        val offsets = ArrayList<Int>(addressCount - 2)
        for (distance in 1 until addressCount) {
            val lower = localOffset - distance
            if (lower > 0) offsets += lower
            val upper = localOffset + distance
            if (upper < addressCount - 1) offsets += upper
        }
        return offsets
    }

    private fun scanAddress(address: String) {
        if (!running.get()) return
        val response = postRegistration(address, LocalSendProtocol.DEFAULT_PORT, "https") ?: return
        val alias = response.body.alias ?: return
        val version = response.body.version ?: return
        val fingerprint = response.peerFingerprint ?: response.body.fingerprint ?: return
        registerDevice(
            DeviceInfo(
                alias = alias,
                version = version,
                deviceModel = response.body.deviceModel,
                deviceType = response.body.deviceType,
                fingerprint = fingerprint,
                port = LocalSendProtocol.DEFAULT_PORT,
                protocol = "https",
                download = response.body.download
            ),
            address
        )
    }

    private fun sendAnnouncement(announce: Boolean) {
        val multicastSocket = socket ?: return
        val message = gson.toJson(identity.deviceInfo().copy(announce = announce))
        val data = message.toByteArray(Charsets.UTF_8)
        val packet = DatagramPacket(
            data,
            data.size,
            InetAddress.getByName(LocalSendProtocol.MULTICAST_ADDRESS),
            LocalSendProtocol.DEFAULT_PORT
        )
        try {
            multicastSocket.send(packet)
            Log.d(TAG, "Sent LocalSend multicast announcement")
        } catch (exception: IOException) {
            reportError("Unable to send device announcement")
        }
    }

    private fun registerDevice(device: DeviceInfo, address: String) {
        if (!isCompatible(device) || device.fingerprint == identity.deviceInfo().fingerprint) return
        val key = device.fingerprint.ifBlank { "$address:${device.port}" }
        devices[key] = RemoteDevice(
            alias = device.alias,
            deviceModel = device.deviceModel,
            deviceType = device.deviceType,
            fingerprint = device.fingerprint,
            address = address,
            port = device.port,
            protocol = device.protocol,
            downloadEnabled = device.download
        )
        publishDevices()
    }

    private fun isCompatible(device: DeviceInfo): Boolean =
        device.version.substringBefore('.') == LocalSendProtocol.VERSION.substringBefore('.') &&
            device.port in 1..65535 &&
            device.protocol in SUPPORTED_PROTOCOLS

    private fun publishDevices() {
        val discovered = devices.values.sortedBy { it.alias.lowercase() }
        mainHandler.post { listener.onDevicesChanged(discovered) }
    }

    private fun reportError(message: String) {
        mainHandler.post { listener.onDiscoveryError(message) }
    }

    private fun acquireMulticastLock() {
        val wifiManager = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        multicastLock = wifiManager.createMulticastLock("localsend-discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    /**
     * Android often creates an IPv6 dual-stack socket when given a wildcard address.
     * LocalSend v2 discovery is IPv4 multicast, so explicitly use the active Wi-Fi IPv4
     * interface for both joining and sending. This is required on several MIUI devices.
     */
    @Throws(IOException::class)
    private fun createMulticastSocket(): MulticastSocket {
        val transport = findWifiTransport()
        val group = InetAddress.getByName(LocalSendProtocol.MULTICAST_ADDRESS)
        return MulticastSocket(null).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                // DatagramSocket binding was added in API 22.
                transport.network.bindSocket(this)
            } else {
                // Android 5.0 has no Network.bindSocket(DatagramSocket). Bind the process
                // before creating/using the multicast socket instead.
                if (!ConnectivityManager.setProcessDefaultNetwork(transport.network)) {
                    throw IOException("Unable to bind process to Wi-Fi network")
                }
                legacyProcessNetworkBound = true
            }
            reuseAddress = true
            bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), LocalSendProtocol.DEFAULT_PORT))
            networkInterface = transport.networkInterface
            timeToLive = 1
            loopbackMode = false
            joinGroup(
                InetSocketAddress(group, LocalSendProtocol.DEFAULT_PORT),
                transport.networkInterface
            )
        }
    }

    @Throws(IOException::class)
    private fun findWifiTransport(): WifiTransport {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: throw IOException("Connectivity service is unavailable")
        val wifiNetwork = connectivityManager.allNetworks.firstOrNull { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } ?: throw IOException("Connect to a Wi-Fi network before starting discovery")
        val wifiLinkAddress = connectivityManager.getLinkProperties(wifiNetwork)
            ?.linkAddresses
            ?.firstOrNull { linkAddress ->
                linkAddress.address is Inet4Address && !linkAddress.address.isLoopbackAddress
            }
            ?: throw IOException("Wi-Fi has no IPv4 address")
        val wifiAddress = wifiLinkAddress.address as Inet4Address
        val networkInterface = NetworkInterface.getByInetAddress(wifiAddress)
            ?: throw IOException("Wi-Fi network interface is unavailable")
        if (!networkInterface.isUp || !networkInterface.supportsMulticast()) {
            throw IOException("Wi-Fi network interface does not support multicast")
        }
        return WifiTransport(wifiNetwork, networkInterface, wifiAddress, wifiLinkAddress.prefixLength)
    }

    private fun ipv4ToInt(address: Inet4Address): Int {
        val bytes = address.address
        return ((bytes[0].toInt() and 0xff) shl 24) or
            ((bytes[1].toInt() and 0xff) shl 16) or
            ((bytes[2].toInt() and 0xff) shl 8) or
            (bytes[3].toInt() and 0xff)
    }

    private fun intToIpv4(value: Int): String = listOf(
        value ushr 24,
        (value ushr 16) and 0xff,
        (value ushr 8) and 0xff,
        value and 0xff
    ).joinToString(".")

    private companion object {
        const val TAG = "LocalSendDiscovery"
        const val MAX_PACKET_SIZE = 8 * 1024
        const val SOCKET_READ_TIMEOUT_MS = 60_000
        // The HTTP legacy scan is a fallback and must not race the multicast announcement burst.
        const val LEGACY_SCAN_DELAY_MS = 2_500L
        const val LEGACY_SCAN_PARALLELISM = 24
        val MULTICAST_RETRY_DELAYS_MS = longArrayOf(500L, 1_500L)
        const val MINIMUM_SCAN_PREFIX = 24
        val JSON_MEDIA_TYPE: MediaType = MediaType.parse("application/json; charset=utf-8")!!
        val SUPPORTED_PROTOCOLS = setOf("http", "https")
    }

    private data class WifiTransport(
        val network: Network,
        val networkInterface: NetworkInterface,
        val address: Inet4Address,
        val prefixLength: Int
    )

    private data class RegistrationResult(
        val body: RegisterResponse,
        val peerFingerprint: String?
    )
}
