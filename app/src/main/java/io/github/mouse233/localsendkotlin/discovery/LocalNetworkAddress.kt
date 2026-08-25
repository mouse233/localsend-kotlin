package io.github.mouse233.localsendkotlin.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.github.mouse233.localsendkotlin.settings.AppSettings
import java.net.Inet4Address

data class LocalNetworkEndpoint(
    val interfaceName: String,
    val address: String
)

/** Finds the addresses used by the LocalSend listeners. */
object LocalNetworkAddress {
    @Suppress("DEPRECATION")
    fun endpoints(context: Context): List<LocalNetworkEndpoint> {
        val interfaces = NetworkInterfaceCatalog.list()
        val configuredSelection = AppSettings(context).networkInterfaceSelection()
        val selected = NetworkInterfaceCatalog.resolveSelection(
            interfaces,
            configuredSelection,
            NetworkInterfaceCatalog.defaultSelection(context, interfaces)
        )
        val selectedEndpoints = interfaces.asSequence()
            .filter { it.name in selected }
            .flatMap { networkInterface ->
                networkInterface.addresses.asSequence().map { address ->
                    LocalNetworkEndpoint(networkInterface.name, address)
                }
            }
            .distinct()
            .toList()
        if (selectedEndpoints.isNotEmpty() || configuredSelection != null) return selectedEndpoints

        val manager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networks = manager.allNetworks.sortedByDescending { network ->
            val capabilities = manager.getNetworkCapabilities(network)
            when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> 2
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> 1
                else -> 0
            }
        }
        return networks.asSequence()
            .mapNotNull { manager.getLinkProperties(it) }
            .flatMap { it.linkAddresses.asSequence() }
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.let { address ->
                listOf(LocalNetworkEndpoint("network", address.hostAddress ?: return@let emptyList()))
            }
            ?: emptyList()
    }

    fun ipv4(context: Context): String? = endpoints(context)
        .firstOrNull { !it.address.contains(':') }
        ?.address
}
