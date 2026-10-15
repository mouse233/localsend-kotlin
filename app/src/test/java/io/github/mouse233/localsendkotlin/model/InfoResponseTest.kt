package io.github.mouse233.localsendkotlin.model

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class InfoResponseTest {
    private val gson = Gson()
    private val device = DeviceInfo(
        alias = "小米 LocalSend",
        version = "2.0",
        deviceModel = "Xiaomi",
        deviceType = "mobile",
        fingerprint = "ABCDEF0123456789",
        port = 53317,
        protocol = "https",
        download = false,
        announce = true
    )

    @Test
    fun responseContainsProtocolFieldsWithoutAnnouncementOrEndpointFields() {
        val json = json(device)
        assertEquals(setOf("alias", "version", "deviceModel", "deviceType", "fingerprint", "download"), json.keySet())
        assertEquals(device.alias, json["alias"].asString)
        assertEquals(device.version, json["version"].asString)
        assertEquals(device.deviceModel, json["deviceModel"].asString)
        assertEquals(device.deviceType, json["deviceType"].asString)
        assertEquals(device.fingerprint, json["fingerprint"].asString)
        assertFalse(json["download"].asBoolean)
    }

    @Test
    fun httpResponsePreservesRandomIdentityAndOptionalDeviceDetails() {
        val json = json(device.copy(protocol = "http", fingerprint = "random-http-identity", deviceModel = null, deviceType = null))
        assertEquals("random-http-identity", json["fingerprint"].asString)
        assertFalse(json.has("deviceModel"))
        assertFalse(json.has("deviceType"))
    }

    private fun json(device: DeviceInfo): JsonObject =
        gson.fromJson(gson.toJson(InfoResponse.from(device)), JsonObject::class.java)
}
