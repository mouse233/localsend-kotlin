package io.github.mouse233.localsendkotlin.history

import android.content.Context
import android.text.format.DateFormat
import io.github.mouse233.localsendkotlin.R
import java.util.Date

internal object ReceiveHistoryText {
    // Keep the persisted placeholder readable without rewriting existing history records.
    const val LEGACY_UNKNOWN_SENDER = "未知设备"

    fun sender(context: Context, alias: String): String =
        if (alias.isBlank() || alias == LEGACY_UNKNOWN_SENDER) context.getString(R.string.unknown_sender) else alias

    fun receivedAt(context: Context, timestamp: Long): String {
        val date = Date(timestamp)
        return "${DateFormat.getDateFormat(context).format(date)} ${DateFormat.getTimeFormat(context).format(date)}"
    }
}
