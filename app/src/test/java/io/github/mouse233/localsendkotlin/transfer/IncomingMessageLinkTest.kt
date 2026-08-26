package io.github.mouse233.localsendkotlin.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingMessageLinkTest {
    @Test
    fun detectsHttpAndCustomUris() {
        assertEquals("https://google.com", IncomingMessageLink.detect("https://google.com"))
        assertEquals("obsidian://open?vault=notes", IncomingMessageLink.detect("obsidian://open?vault=notes"))
    }

    @Test
    fun rejectsPlainTextAndUrisWithWhitespace() {
        assertNull(IncomingMessageLink.detect("hello there"))
        assertNull(IncomingMessageLink.detect("https://example.com/a page"))
    }
}
