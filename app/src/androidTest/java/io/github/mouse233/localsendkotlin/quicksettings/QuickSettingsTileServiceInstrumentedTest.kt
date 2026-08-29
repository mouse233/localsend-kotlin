package io.github.mouse233.localsendkotlin.quicksettings

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mouse233.localsendkotlin.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickSettingsTileServiceInstrumentedTest {
    @Test
    fun quickSettingsActionResolvesToTheTileService() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val services = context.packageManager.queryIntentServices(
            Intent("android.service.quicksettings.action.QS_TILE"),
            0
        )
        val service = services.firstOrNull { it.serviceInfo.name == QuickSettingsTileService::class.java.name }
        assertTrue(service != null)
        assertEquals(
            context.getString(R.string.quick_settings_tile_label),
            service?.serviceInfo?.loadLabel(context.packageManager)
        )
        assertEquals(
            "android.permission.BIND_QUICK_SETTINGS_TILE",
            service?.serviceInfo?.permission
        )
    }
}
