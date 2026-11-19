package io.github.mouse233.localsendkotlin.model

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceInfoPayloadTest {
    private val gson = Gson()
    private val completeJson = """
        {
          "alias": "Nice Orange",
          "version": "2.1",
          "deviceModel": "Samsung",
          "deviceType": "mobile",
          "fingerprint": "ABCDEF0123456789",
          "port": 53317,
          "protocol": "https",
          "download": true,
          "announce": true
        }
    """.trimIndent()

    @Test
    fun completeDeviceJsonIsConverted() {
        assertEquals(
            DeviceInfo(
                alias = "Nice Orange",
                version = "2.1",
                deviceModel = "Samsung",
                deviceType = "mobile",
                fingerprint = "ABCDEF0123456789",
                port = 53317,
                protocol = "https",
                download = true,
                announce = true
            ),
            parse(completeJson)
        )
    }

    @Test
    fun missingRequiredFieldIsRejected() {
        listOf("alias", "version", "fingerprint", "port", "protocol").forEach { field ->
            assertNull("missing $field must be rejected", parse(without(field)))
        }
    }

    @Test
    fun explicitNullRequiredFieldIsRejected() {
        val json = gson.fromJson(completeJson, JsonObject::class.java).apply { add("alias", null) }
        assertNull(parse(json.toString()))
    }

    @Test
    fun optionalFieldsMayBeOmitted() {
        val device = parse(without("deviceModel", "deviceType", "download", "announce"))!!
        assertNull(device.deviceModel)
        assertNull(device.deviceType)
        assertNull(device.announce)
        assertFalse(device.download)
    }

    private fun parse(json: String): DeviceInfo? =
        gson.fromJson(json, DeviceInfoPayload::class.java)?.toDeviceInfo()

    private fun without(vararg fields: String): String =
        gson.fromJson(completeJson, JsonObject::class.java).apply { fields.forEach { remove(it) } }.toString()
}
