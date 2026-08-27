package io.github.mouse233.localsendkotlin.settings

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mouse233.localsendkotlin.model.RemoteDevice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppSettingsTest {
    private lateinit var preferences: android.content.SharedPreferences

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        preferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun screenAwakeIsDisabledByDefault() {
        assertFalse(AppSettings(InstrumentationRegistry.getInstrumentation().targetContext).keepScreenAwakeDuringTransfer())
    }

    @Test
    fun screenAwakeSettingPersists() {
        val settings = AppSettings(InstrumentationRegistry.getInstrumentation().targetContext)
        settings.setKeepScreenAwakeDuringTransfer(true)
        assertTrue(settings.keepScreenAwakeDuringTransfer())
    }

    @Test
    fun themeColorSettingPersists() {
        val settings = AppSettings(InstrumentationRegistry.getInstrumentation().targetContext)
        settings.setThemeColor(ThemeColorPreset.PURPLE.id)
        org.junit.Assert.assertEquals(ThemeColorPreset.PURPLE.id, settings.themeColor())
    }

    @Test
    fun themeColorDefaultsToBlueGrey() {
        org.junit.Assert.assertEquals(
            ThemeColorPreset.BLUE_GREY.id,
            AppSettings(InstrumentationRegistry.getInstrumentation().targetContext).themeColor()
        )
    }

    @Test
    fun darkModeDefaultsToFollowSystem() {
        org.junit.Assert.assertEquals(
            DarkModePreference.FOLLOW_SYSTEM.id,
            AppSettings(InstrumentationRegistry.getInstrumentation().targetContext).darkMode()
        )
    }

    @Test
    fun darkModeSettingPersists() {
        val settings = AppSettings(InstrumentationRegistry.getInstrumentation().targetContext)
        settings.setDarkMode(DarkModePreference.ENABLED.id)
        org.junit.Assert.assertEquals(DarkModePreference.ENABLED.id, settings.darkMode())
    }

    @Test
    fun favoriteAutoSaveSettingPersists() {
        val settings = AppSettings(InstrumentationRegistry.getInstrumentation().targetContext)
        settings.setAutoSaveFavoriteReceivedFiles(true)
        assertTrue(settings.autoSaveFavoriteReceivedFiles())
    }

    @Test
    fun favoriteCanBeEditedAndRemovedByFingerprint() {
        val settings = AppSettings(InstrumentationRegistry.getInstrumentation().targetContext)
        val device = RemoteDevice("Original", "Android", "mobile", "ABC123", "192.168.1.10", 53317, "https", true)

        assertTrue(settings.toggleFavorite(device))
        val favorite = settings.favoriteDevices().single()
        assertTrue(settings.updateFavorite(favorite, "Saved name", "192.168.1.20", 53318))
        assertEquals("Saved name", settings.favoriteDevices().single().alias)
        assertEquals("192.168.1.20", settings.favoriteDevices().single().address)
        assertEquals(53318, settings.favoriteDevices().single().port)
        assertTrue(settings.removeFavorite("abc123"))
        assertTrue(settings.favoriteDevices().isEmpty())
    }
}
