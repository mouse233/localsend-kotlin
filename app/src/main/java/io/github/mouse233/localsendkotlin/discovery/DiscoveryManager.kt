package io.github.mouse233.localsendkotlin.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
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
    private val httpsClient = identity.tlsIdentity.createSslContext().let { sslContext ->
        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, identity.tlsIdentity.trustManager)
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
    @Volatile private var multicastLock: WifiManager.MulticastLock? = null

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
            val localServer = LocalSendServer(
                gson,
                identity.tlsIdentity,
                identity::deviceInfo,
                ::registerDevice
            )
            localServer.start(SERVER_START_TIMEOUT_MS, false)
            server = localServer

            val multicastSocket = createMulticastSocket()
            socket = multicastSocket
            Log.i(TAG, "Listening for LocalSend discovery on ${multicastSocket.networkInterface.name}")
            sendAnnouncement(announce = true)
            scheduleLegacyScan()
            receiveAnnouncements(multicastSocket)
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to start LocalSend discovery", exception)
            reportError("Unable to start discovery: ${exception.message ?: "network error"}")
        } finally {
            stop()
        }
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
        return postRegistration(address, device.port, device.protocol) != null
    }

    private fun postRegistration(address: String, port: Int, protocol: String): RegisterResponse? {
        val requestBody = RequestBody.create(JSON_MEDIA_TYPE, gson.toJson(identity.deviceInfo()))
        val request = Request.Builder()
            .url("$protocol://$address:$port${LocalSendProtocol.REGISTER_PATH}")
            .post(requestBody)
            .build()
        return try {
            val client = if (protocol == "https") httpsClient else httpClient
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                gson.fromJson(response.body()?.charStream(), RegisterResponse::class.java)
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
        Log.i(TAG, "Starting HTTP legacy scan with ${addressCount - 2} hosts")
        for (offset in 1 until addressCount - 1) {
            val address = intToIpv4(networkAddress + offset)
            if (address == transport.address.hostAddress) continue
            legacyScanExecutor?.execute { scanAddress(address) }
        }
    }

    private fun scanAddress(address: String) {
        if (!running.get()) return
        val response = postRegistration(address, LocalSendProtocol.DEFAULT_PORT, "https") ?: return
        val alias = response.alias ?: return
        val version = response.version ?: return
        val fingerprint = response.fingerprint ?: return
        registerDevice(
            DeviceInfo(
                alias = alias,
                version = version,
                deviceModel = response.deviceModel,
                deviceType = response.deviceType,
                fingerprint = fingerprint,
                port = LocalSendProtocol.DEFAULT_PORT,
                protocol = "https",
                download = response.download
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
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
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
            transport.network.bindSocket(this)
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
        const val SERVER_START_TIMEOUT_MS = 5_000
        const val LEGACY_SCAN_DELAY_MS = 2_000L
        const val LEGACY_SCAN_PARALLELISM = 12
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
}
