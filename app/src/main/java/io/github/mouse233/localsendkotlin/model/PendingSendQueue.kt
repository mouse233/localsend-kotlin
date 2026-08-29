package io.github.mouse233.localsendkotlin.model

import android.net.Uri

/** Keeps the draft files shown in the home screen's pending-send sheet. */
class PendingSendQueue {
    private val state = PendingSendQueueState<PendingSendFile>()

    fun replace(newFiles: List<PendingSendFile>) {
        state.replace(newFiles.map { it.uri.toString() to it })
    }

    fun remove(uri: Uri): Boolean = state.remove(uri.toString())

    fun clear() = state.clear()

    fun snapshot(): List<PendingSendFile> = state.snapshot()
}
