package io.github.mouse233.localsendkotlin.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address

/** Finds the LAN IPv4 address used by the LocalSend listener. */
object LocalNetworkAddress {
    @Suppress("DEPRECATION")
    fun ipv4(context: Context): String? {
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
            ?.hostAddress
    }
}
