package io.github.mouse233.localsendkotlin.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualEndpointTest {
    @Test
    fun parsesIpv4AddressAndPort() {
        assertEquals(ManualEndpoint("192.168.1.10", 53317), ManualEndpoint.parse(" 192.168.1.10:53317 "))
    }

    @Test
    fun parsesBracketedIpv6AddressAndBuildsUrl() {
        val endpoint = ManualEndpoint.parse("[fe80::1234]:53317")
        assertEquals("fe80::1234", endpoint.host)
        assertEquals("https://[fe80::1234]:53317/api/test", endpoint.url("https", "/api/test"))
    }

    @Test
    fun matchesDiscoveredHostAndPort() {
        assertTrue(ManualEndpoint.parse("[fe80::1234]:53317").matches("fe80::1234", 53317))
        assertFalse(ManualEndpoint.parse("10.0.0.102:53317").matches("10.0.0.103", 53317))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnbracketedIpv6Address() {
        ManualEndpoint.parse("fe80::1234:53317")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidPort() {
        ManualEndpoint.parse("192.168.1.10:65536")
    }
}
