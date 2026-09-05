package io.github.mouse233.localsendkotlin.model

/**
 * Completes only the supplied outgoing sessions, leaving older terminal states untouched.
 * The caller owns synchronization for the mutable queue map.
 */
internal fun <Queue : MutableMap<String, ActiveTransferFile>> finalizeOutgoingSessions(
    outgoingFiles: MutableMap<String, Queue>,
    sessionIds: Set<String>,
    status: ActiveTransferFile.Status
): List<ActiveTransferFile> {
    val updated = mutableListOf<ActiveTransferFile>()
    sessionIds.forEach { sessionId ->
        outgoingFiles[sessionId]?.values?.toList()?.forEach { file ->
            if (file.status != ActiveTransferFile.Status.WAITING &&
                file.status != ActiveTransferFile.Status.TRANSFERRING
            ) return@forEach
            val finished = file.copy(
                receivedBytes = if (status == ActiveTransferFile.Status.COMPLETED) file.totalBytes else file.receivedBytes,
                status = status
            )
            outgoingFiles[sessionId]?.set(file.fileId, finished)
            updated += finished
        }
    }
    return updated
}
