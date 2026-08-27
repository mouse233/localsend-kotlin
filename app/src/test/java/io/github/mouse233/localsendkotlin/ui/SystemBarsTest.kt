package io.github.mouse233.localsendkotlin.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemBarsTest {
    private val lightBackground = 0xFFF5F7FA.toInt()
    private val darkBackground = 0xFF111111.toInt()

    @Test
    fun lightThemeUsesDarkNavigationBackgroundBeforeOreo() {
        assertEquals(0xFF000000.toInt(), SystemBars.navigationBarColor(lightBackground, false, 25))
    }

    @Test
    fun lightThemeKeepsLightNavigationBackgroundFromOreo() {
        assertEquals(lightBackground, SystemBars.navigationBarColor(lightBackground, false, 26))
    }

    @Test
    fun darkThemeKeepsDarkNavigationBackgroundOnAllSupportedApis() {
        assertEquals(darkBackground, SystemBars.navigationBarColor(darkBackground, true, 25))
        assertEquals(darkBackground, SystemBars.navigationBarColor(darkBackground, true, 26))
    }
}
