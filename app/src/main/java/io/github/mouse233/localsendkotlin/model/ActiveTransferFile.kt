package io.github.mouse233.localsendkotlin.model

/** Live state for one file inside an incoming transfer session. */
data class ActiveTransferFile(
    val sessionId: String,
    val fileId: String,
    val fileName: String,
    val receivedBytes: Long,
    val totalBytes: Long,
    val status: Status
) {
    enum class Status { WAITING, TRANSFERRING, COMPLETED, CANCELLED, FAILED }
}
