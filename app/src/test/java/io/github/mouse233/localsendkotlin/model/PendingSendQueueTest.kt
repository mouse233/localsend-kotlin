package io.github.mouse233.localsendkotlin.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingSendQueueTest {
    private val first = "first.txt"
    private val second = "second.txt"

    @Test
    fun replacePreservesOrderAndRemovesDuplicatesByUri() {
        val queue = PendingSendQueueState<String>()

        queue.replace(listOf("first" to first, "second" to second, "first" to "renamed.txt"))

        assertEquals(listOf("renamed.txt", second), queue.snapshot())
    }

    @Test
    fun removeAndClearUpdateQueue() {
        val queue = PendingSendQueueState<String>()
        queue.replace(listOf("first" to first, "second" to second))

        assertTrue(queue.remove("first"))
        assertFalse(queue.remove("first"))
        assertEquals(listOf(second), queue.snapshot())

        queue.clear()
        assertTrue(queue.snapshot().isEmpty())
    }
}
