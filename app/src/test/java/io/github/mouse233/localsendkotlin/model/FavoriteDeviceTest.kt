package io.github.mouse233.localsendkotlin.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteDeviceTest {
    @Test
    fun fingerprintIsTheCaseInsensitiveDeviceIdentity() {
        val favorite = FavoriteDevice("ABC123", "Old name", "192.168.1.10", 53317, "https")
        assertTrue(favorite.matches(device(fingerprint = "abc123")))
        assertFalse(favorite.matches(device(fingerprint = "different")))
    }

    @Test
    fun discoveredAliasCanBeRefreshedWithoutChangingSavedEndpoint() {
        val favorite = FavoriteDevice("ABC123", "Old name", "192.168.1.10", 53317, "https")
        val refreshed = favorite.refreshedFrom(device(alias = "New name", address = "192.168.1.20", port = 53318))

        assertEquals("ABC123", refreshed.fingerprint)
        assertEquals("New name", refreshed.alias)
        assertEquals("192.168.1.10", refreshed.address)
        assertEquals(53317, refreshed.port)
    }

    @Test
    fun customAliasAndSavedEndpointAreKeptWhenRediscovered() {
        val favorite = FavoriteDevice("ABC123", "My saved name", "192.168.1.10", 53317, "https", customAlias = true)
        val refreshed = favorite.refreshedFrom(device(alias = "Discovered name", address = "192.168.1.20"))

        assertEquals("My saved name", refreshed.alias)
        assertEquals("192.168.1.10", refreshed.address)
    }

    @Test
    fun savedEndpointIsKeptWhenDeviceIsRediscovered() {
        val favorite = FavoriteDevice("ABC123", "Peer", "100.64.0.10", 53317, "https")
        val refreshed = favorite.refreshedFrom(device(address = "192.168.1.20", port = 53318))

        assertEquals("100.64.0.10", refreshed.address)
        assertEquals(53317, refreshed.port)
        assertEquals("https", refreshed.protocol)
    }

    private fun device(
        alias: String = "Peer",
        address: String = "192.168.1.10",
        port: Int = 53317,
        fingerprint: String = "ABC123"
    ) = RemoteDevice(alias, "Android", "mobile", fingerprint, address, port, "https", true)
}
