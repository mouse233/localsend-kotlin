package io.github.mouse233.localsendkotlin.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

/** A local interface and all of its usable non-loopback addresses. */
data class LocalNetworkInterface(
    val name: String,
    val addresses: List<String>,
    val ipv4Addresses: List<LocalIpv4Address>
)

data class LocalIpv4Address(
    val address: Inet4Address,
    val prefixLength: Int
)

/** Enumerates interfaces for the settings screen and network listener. */
object NetworkInterfaceCatalog {
    fun list(): List<LocalNetworkInterface> {
        val enumeration = try {
            NetworkInterface.getNetworkInterfaces()
        } catch (_: Exception) {
            return emptyList()
        } ?: return emptyList()
        return enumeration.toList().mapNotNull outer@ { networkInterface ->
            val addresses = networkInterface.inetAddresses.toList()
                .filterNot(InetAddress::isLoopbackAddress)
                .filterNot(InetAddress::isAnyLocalAddress)
            if (addresses.isEmpty() || !isUsable(networkInterface)) return@outer null
            LocalNetworkInterface(
                name = networkInterface.name,
                addresses = addresses.map { displayAddress(networkInterface.name, it) },
                ipv4Addresses = networkInterface.interfaceAddresses.mapNotNull inner@ { interfaceAddress ->
                    val address = interfaceAddress.address as? Inet4Address ?: return@inner null
                    if (address.isLoopbackAddress) return@inner null
                    LocalIpv4Address(address, interfaceAddress.networkPrefixLength.toInt().coerceIn(0, 32))
                }
            )
        }
    }

    /** Prefer Wi-Fi/Ethernet on first launch, matching the safe default used by the app today. */
    fun defaultSelection(context: Context, interfaces: List<LocalNetworkInterface> = list()): Set<String> {
        val manager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val preferredNames = manager?.allNetworks.orEmpty().mapNotNull { network ->
            val capabilities = manager?.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) return@mapNotNull null
            manager.getLinkProperties(network)?.interfaceName
        }.toSet()
        val availablePreferred = interfaces.map { it.name }.filter(preferredNames::contains).toSet()
        return availablePreferred.ifEmpty { interfaces.map { it.name }.toSet() }
    }

    /** Resolves stored names against the interfaces that are currently available. */
    fun resolveSelection(
        interfaces: List<LocalNetworkInterface>,
        configuredNames: Set<String>?,
        fallbackNames: Set<String>
    ): Set<String> {
        val availableNames = interfaces.map { it.name }.toSet()
        return (configuredNames ?: fallbackNames).intersect(availableNames)
    }

    private fun isUsable(networkInterface: NetworkInterface): Boolean = try {
        networkInterface.isUp && !networkInterface.isLoopback
    } catch (_: Exception) {
        false
    }

    private fun displayAddress(interfaceName: String, address: InetAddress): String {
        val host = address.hostAddress ?: return address.toString()
        return if (address is Inet6Address && address.isLinkLocalAddress && !host.contains('%')) {
            "$host%$interfaceName"
        } else {
            host
        }
    }
}
