package io.github.mouse233.localsendkotlin.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTypeTest {
    @Test
    fun exposesAllLocalSendDeviceTypes() {
        assertEquals(
            listOf("mobile", "desktop", "web", "headless", "server"),
            DeviceType.values().map(DeviceType::value)
        )
    }

    @Test
    fun unknownDeviceTypeFallsBackToMobile() {
        assertEquals(DeviceType.MOBILE, DeviceType.fromValue("unknown"))
    }
}
