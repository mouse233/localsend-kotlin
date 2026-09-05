package io.github.mouse233.localsendkotlin.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferSessionTest {
    @Test
    fun groupsFilesByDirectionAndSessionInFirstSeenOrder() {
        val files = listOf(
            ActiveTransferFile("receive", "a", "a.txt", 1L, 2L, ActiveTransferFile.Status.TRANSFERRING),
            ActiveTransferFile("send", "b", "b.txt", 0L, 4L, ActiveTransferFile.Status.WAITING, ActiveTransferFile.Direction.OUTGOING),
            ActiveTransferFile("receive", "c", "c.txt", 0L, 3L, ActiveTransferFile.Status.WAITING),
            ActiveTransferFile("send", "d", "d.txt", 4L, 4L, ActiveTransferFile.Status.COMPLETED, ActiveTransferFile.Direction.OUTGOING)
        )

        val sessions = groupTransferSessions(files)

        assertEquals(listOf("receive", "send"), sessions.map { it.sessionId })
        assertEquals(listOf("a.txt", "c.txt"), sessions[0].files.map { it.fileName })
        assertEquals(listOf("b.txt", "d.txt"), sessions[1].files.map { it.fileName })
        assertEquals(ActiveTransferFile.Direction.OUTGOING, sessions[1].direction)
    }

    @Test
    fun marksSessionCancelledOnlyWhenNoFilesAreStillActive() {
        val cancelled = TransferSession(
            "session",
            ActiveTransferFile.Direction.INCOMING,
            listOf(
                ActiveTransferFile("session", "a", "a.txt", 2L, 2L, ActiveTransferFile.Status.CANCELLED),
                ActiveTransferFile("session", "b", "b.txt", 0L, 3L, ActiveTransferFile.Status.COMPLETED)
            )
        )
        val stillActive = cancelled.copy(files = cancelled.files + ActiveTransferFile(
            "session", "c", "c.txt", 0L, 4L, ActiveTransferFile.Status.WAITING
        ))

        assertEquals(true, cancelled.isCancelled())
        assertEquals(false, stillActive.isCancelled())
    }

    @Test
    fun finalizingOneOutgoingSessionDoesNotChangeOlderHistory() {
        val oldSession = linkedMapOf(
            "old-file" to ActiveTransferFile(
                "old", "old-file", "old.txt", 1L, 2L,
                ActiveTransferFile.Status.FAILED,
                ActiveTransferFile.Direction.OUTGOING
            )
        )
        val currentSession = linkedMapOf(
            "current-file" to ActiveTransferFile(
                "current", "current-file", "current.txt", 0L, 4L,
                ActiveTransferFile.Status.TRANSFERRING,
                ActiveTransferFile.Direction.OUTGOING
            )
        )
        val queues = linkedMapOf("old" to oldSession, "current" to currentSession)

        val updated = finalizeOutgoingSessions(
            queues,
            setOf("current"),
            ActiveTransferFile.Status.COMPLETED
        )

        assertEquals(1, updated.size)
        assertEquals(ActiveTransferFile.Status.FAILED, queues["old"]!!["old-file"]!!.status)
        assertEquals(ActiveTransferFile.Status.COMPLETED, queues["current"]!!["current-file"]!!.status)
        assertEquals(4L, queues["current"]!!["current-file"]!!.receivedBytes)
    }

    @Test
    fun finalizingSessionKeepsAlreadyTerminalFilesUntouched() {
        val queues = linkedMapOf(
            "current" to linkedMapOf(
                "done" to ActiveTransferFile(
                    "current", "done", "done.txt", 3L, 3L,
                    ActiveTransferFile.Status.COMPLETED,
                    ActiveTransferFile.Direction.OUTGOING
                ),
                "cancelled" to ActiveTransferFile(
                    "current", "cancelled", "cancelled.txt", 1L, 5L,
                    ActiveTransferFile.Status.CANCELLED,
                    ActiveTransferFile.Direction.OUTGOING
                )
            )
        )

        val updated = finalizeOutgoingSessions(
            queues,
            setOf("current"),
            ActiveTransferFile.Status.FAILED
        )

        assertTrue(updated.isEmpty())
        assertEquals(ActiveTransferFile.Status.COMPLETED, queues["current"]!!["done"]!!.status)
        assertEquals(ActiveTransferFile.Status.CANCELLED, queues["current"]!!["cancelled"]!!.status)
    }
}
