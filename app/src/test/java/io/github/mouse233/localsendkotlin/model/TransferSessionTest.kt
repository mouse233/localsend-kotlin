package io.github.mouse233.localsendkotlin.model

import org.junit.Assert.assertEquals
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
}
