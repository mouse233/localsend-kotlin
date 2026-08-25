package io.github.mouse233.localsendkotlin.ui

import io.github.mouse233.localsendkotlin.R

/** Shared file-type icon mapping for history and pending receive rows. */
object FileTypeIcon {
    fun forMimeType(mimeType: String): Int = when {
        mimeType.startsWith("image/", ignoreCase = true) -> R.drawable.ic_history_image
        mimeType.startsWith("text/", ignoreCase = true) ||
            mimeType.contains("pdf", ignoreCase = true) ||
            mimeType.contains("word", ignoreCase = true) -> R.drawable.ic_history_text
        else -> R.drawable.ic_history_file
    }
}
