package io.github.mouse233.localsendkotlin.sharing

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareIntentParserInstrumentedTest {
    @Test
    fun parsesMultipleUrisFromExtrasAndClipData() {
        val first = Uri.parse("content://example/first")
        val second = Uri.parse("content://example/second")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
            clipData = ClipData.newRawUri("shared", first)
        }

        val content = ShareIntentParser.parse(intent)

        assertNotNull(content)
        assertEquals(listOf(first, second), content?.uris)
    }

    @Test
    fun parsesSharedText() {
        val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "hello")

        val content = ShareIntentParser.parse(intent)

        assertNotNull(content)
        assertTrue(content?.uris.isNullOrEmpty())
        assertEquals("hello", content?.text)
    }
}
