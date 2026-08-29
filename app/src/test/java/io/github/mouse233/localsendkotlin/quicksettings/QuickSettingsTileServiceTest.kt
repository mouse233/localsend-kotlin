package io.github.mouse233.localsendkotlin.quicksettings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickSettingsTileServiceTest {
    @Test
    fun tileIsActiveWhenTheServiceIsRunning() {
        assertTrue(QuickSettingsTileState.shouldBeActive(serviceRunning = true, appForeground = false))
    }

    @Test
    fun tileIsActiveWhenTheAppIsInTheForeground() {
        assertTrue(QuickSettingsTileState.shouldBeActive(serviceRunning = false, appForeground = true))
    }

    @Test
    fun tileIsInactiveWhenNeitherServiceNorAppIsActive() {
        assertFalse(QuickSettingsTileState.shouldBeActive(serviceRunning = false, appForeground = false))
    }
}
