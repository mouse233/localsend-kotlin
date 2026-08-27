package io.github.mouse233.localsendkotlin.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorsTest {
    @Test
    fun doesNotRecreateBeforeAnActivityHasAppliedItsInitialMode() {
        assertFalse(ThemeColors.needsActivityRecreate(null, false))
    }

    @Test
    fun recreatesOnlyWhenTheResolvedModeChanges() {
        assertFalse(ThemeColors.needsActivityRecreate(true, true))
        assertFalse(ThemeColors.needsActivityRecreate(false, false))
        assertTrue(ThemeColors.needsActivityRecreate(true, false))
        assertTrue(ThemeColors.needsActivityRecreate(false, true))
    }
}
