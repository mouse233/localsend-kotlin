package io.github.mouse233.localsendkotlin.discovery

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalNetworkAddressTest {
    private val endpoints = listOf(
        LocalNetworkEndpoint("wlan0", "fe80::1%wlan0"),
        LocalNetworkEndpoint("wlan0", "10.0.0.105"),
        LocalNetworkEndpoint("wlan2", "240e::2")
    )

    @Test
    fun ipv6AddressesRemainVisibleByDefault() {
        assertEquals(endpoints, LocalNetworkAddress.visibleEndpoints(endpoints, hideIpv6 = false))
    }

    @Test
    fun hidingIpv6KeepsOnlyIpv4Addresses() {
        assertEquals(
            listOf(LocalNetworkEndpoint("wlan0", "10.0.0.105")),
            LocalNetworkAddress.visibleEndpoints(endpoints, hideIpv6 = true)
        )
    }
}
