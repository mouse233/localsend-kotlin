package io.github.mouse233.localsendkotlin.model

/** A stable group of files belonging to one incoming or outgoing session. */
data class TransferSession(
    val sessionId: String,
    val direction: ActiveTransferFile.Direction,
    val files: List<ActiveTransferFile>
)

fun groupTransferSessions(files: List<ActiveTransferFile>): List<TransferSession> {
    data class GroupKey(val direction: ActiveTransferFile.Direction, val sessionId: String)
    val groups = LinkedHashMap<GroupKey, MutableList<ActiveTransferFile>>()
    files.forEach { file ->
        groups.getOrPut(GroupKey(file.direction, file.sessionId)) { mutableListOf() }.add(file)
    }
    return groups.map { (key, groupedFiles) ->
        TransferSession(
            sessionId = key.sessionId,
            direction = key.direction,
            files = groupedFiles.toList()
        )
    }
}

fun TransferSession.isCancelled(): Boolean = files.any {
    it.status == ActiveTransferFile.Status.CANCELLED
} && files.none {
    it.status == ActiveTransferFile.Status.WAITING || it.status == ActiveTransferFile.Status.TRANSFERRING
}
