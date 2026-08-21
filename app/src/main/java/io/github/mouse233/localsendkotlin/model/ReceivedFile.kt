package io.github.mouse233.localsendkotlin.model

import android.net.Uri

/** A file owned by this app in the user's Download/LocalSend Kotlin folder. */
data class ReceivedFile(
    val displayName: String,
    val uri: Uri,
    val mimeType: String,
    val size: Long
)
