package io.github.mouse233.localsendkotlin.sharing

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareIntentParserTest {
    @Test
    fun supportsSingleAndMultipleShareActions() {
        assertTrue(ShareIntentParser.supportsAction(Intent.ACTION_SEND))
        assertTrue(ShareIntentParser.supportsAction(Intent.ACTION_SEND_MULTIPLE))
    }

    @Test
    fun rejectsNonShareActions() {
        assertFalse(ShareIntentParser.supportsAction(Intent.ACTION_MAIN))
        assertFalse(ShareIntentParser.supportsAction(null))
    }
}
