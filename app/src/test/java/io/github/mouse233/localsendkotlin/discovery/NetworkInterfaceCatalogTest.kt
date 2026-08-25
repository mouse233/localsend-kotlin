package io.github.mouse233.localsendkotlin.discovery

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkInterfaceCatalogTest {
    private val interfaces = listOf(
        LocalNetworkInterface("rmnet_data2", listOf("10.0.0.2"), emptyList()),
        LocalNetworkInterface("wlan0", listOf("192.168.1.2"), emptyList()),
        LocalNetworkInterface("tun0", listOf("10.8.0.2"), emptyList())
    )

    @Test
    fun resolveSelectionKeepsOnlyAvailableConfiguredInterfaces() {
        assertEquals(
            setOf("wlan0"),
            NetworkInterfaceCatalog.resolveSelection(
                interfaces,
                setOf("wlan0", "missing0"),
                fallbackNames = setOf("rmnet_data2")
            )
        )
    }

    @Test
    fun resolveSelectionUsesFallbackWhenNothingWasConfigured() {
        assertEquals(
            setOf("rmnet_data2", "tun0"),
            NetworkInterfaceCatalog.resolveSelection(
                interfaces,
                configuredNames = null,
                fallbackNames = setOf("rmnet_data2", "tun0")
            )
        )
    }
}
