package io.github.mouse233.localsendkotlin.model

import android.net.Uri

/** A completed file received from another LocalSend device. */
data class ReceiveHistoryEntry(
    val id: Long,
    val displayName: String,
    val uri: Uri,
    val mimeType: String,
    val size: Long,
    val senderAlias: String,
    val receivedAt: Long
)
