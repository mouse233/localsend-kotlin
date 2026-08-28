package io.github.mouse233.localsendkotlin.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.github.mouse233.localsendkotlin.R
import io.github.mouse233.localsendkotlin.model.ActiveTransferFile
import io.github.mouse233.localsendkotlin.model.TransferSession
import io.github.mouse233.localsendkotlin.model.groupTransferSessions
import io.github.mouse233.localsendkotlin.model.isCancelled

class ActiveTransferAdapter(
    private val onCancelFile: (String, String) -> Unit,
    private val onCancelSession: (String) -> Unit
) : RecyclerView.Adapter<ActiveTransferAdapter.ViewHolder>() {
    private val rows = mutableListOf<Row>()
    private val checksumProgress = LinkedHashMap<String, ChecksumProgress>()

    fun setChecksumProgress(sessionId: String, current: Int, total: Int) {
        checksumProgress[sessionId] = ChecksumProgress(current, total)
        val position = rows.indexOfFirst { it is Row.Header && it.session.sessionId == sessionId }
        if (position >= 0) notifyItemChanged(position)
    }

    fun clearChecksumProgress(sessionId: String) {
        if (checksumProgress.remove(sessionId) != null) {
            val position = rows.indexOfFirst { it is Row.Header && it.session.sessionId == sessionId }
            if (position >= 0) notifyItemChanged(position)
        }
    }

    fun submitFiles(newFiles: List<ActiveTransferFile>) {
        val oldRows = rows.toList()
        val newRows = groupTransferSessions(newFiles).flatMap { session ->
            listOf(Row.Header(session)) + session.files.map { Row.File(it) }
        }
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldRows.size
            override fun getNewListSize() = newRows.size
            override fun areItemsTheSame(oldPosition: Int, newPosition: Int) = oldRows[oldPosition].key == newRows[newPosition].key
            override fun areContentsTheSame(oldPosition: Int, newPosition: Int) = oldRows[oldPosition] == newRows[newPosition]
        })
        rows.clear()
        rows.addAll(newRows)
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = if (viewType == VIEW_TYPE_HEADER) R.layout.item_transfer_session else R.layout.item_active_transfer
        val itemView = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        ThemeColors.apply(itemView)
        return if (viewType == VIEW_TYPE_HEADER) HeaderViewHolder(itemView) else FileViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderViewHolder).bind(row.session, checksumProgress[row.session.sessionId], onCancelSession)
            is Row.File -> (holder as FileViewHolder).bind(row.file, onCancelFile)
        }
    }

    override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) VIEW_TYPE_HEADER else VIEW_TYPE_FILE
    override fun getItemCount() = rows.size

    private sealed class Row {
        abstract val key: String

        data class Header(val session: TransferSession) : Row() {
            override val key = "header:${session.direction}:${session.sessionId}"
        }

        data class File(val file: ActiveTransferFile) : Row() {
            override val key = "file:${file.direction}:${file.sessionId}:${file.fileId}"
        }
    }

    open class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    class HeaderViewHolder(itemView: View) : ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.transfer_session_title)
        private val summary: TextView = itemView.findViewById(R.id.transfer_session_summary)
        private val preparationProgress: ProgressBar = itemView.findViewById(R.id.transfer_session_progress)
        private val cancel: Button = itemView.findViewById(R.id.transfer_session_cancel)

        fun bind(session: TransferSession, checksumProgress: ChecksumProgress?, onCancelSession: (String) -> Unit) {
            val isPreparing = session.direction == ActiveTransferFile.Direction.OUTGOING && checksumProgress != null
            if (isPreparing) {
                title.text = itemView.context.getString(R.string.transfer_checksum_progress, checksumProgress!!.current, checksumProgress.total)
            } else {
                title.setText(if (session.direction == ActiveTransferFile.Direction.OUTGOING) R.string.transfer_session_sending else R.string.transfer_session_receiving)
            }
            val total = session.files.sumOf { it.totalBytes }
            val transferred = session.files.sumOf { it.receivedBytes.coerceAtLeast(0L) }
            summary.text = if (session.isCancelled()) {
                itemView.context.getString(R.string.transfer_cancelled)
            } else if (session.files.all { it.totalBytes >= 0L }) {
                itemView.context.getString(R.string.transfer_session_summary, session.files.size, formatBytes(transferred), formatBytes(total))
            } else {
                itemView.context.getString(R.string.transfer_session_summary_unknown, session.files.size)
            }
            summary.visibility = if (isPreparing) View.GONE else View.VISIBLE
            preparationProgress.visibility = if (isPreparing) View.VISIBLE else View.GONE
            if (isPreparing) {
                preparationProgress.progress = (((checksumProgress!!.current - 1) * 100L) / checksumProgress.total.coerceAtLeast(1)).toInt().coerceIn(0, 100)
            }
            val canCancel = session.files.any {
                it.status == ActiveTransferFile.Status.WAITING || it.status == ActiveTransferFile.Status.TRANSFERRING
            }
            cancel.visibility = if (canCancel) View.VISIBLE else View.GONE
            cancel.setText(R.string.cancel_transfer)
            cancel.setOnClickListener { onCancelSession(session.sessionId) }
        }
    }

    data class ChecksumProgress(val current: Int, val total: Int)

    class FileViewHolder(itemView: View) : ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.active_transfer_name)
        private val status: TextView = itemView.findViewById(R.id.active_transfer_status)
        private val progressLabel: TextView = itemView.findViewById(R.id.active_transfer_progress_label)
        private val progress: ProgressBar = itemView.findViewById(R.id.active_transfer_progress)
        private val cancel: Button = itemView.findViewById(R.id.active_transfer_cancel)

        fun bind(file: ActiveTransferFile, onCancelFile: (String, String) -> Unit) {
            name.text = file.fileName
            status.text = when (file.status) {
                ActiveTransferFile.Status.COMPLETED -> itemView.context.getString(if (file.direction == ActiveTransferFile.Direction.OUTGOING) R.string.transfer_sent else R.string.transfer_completed)
                ActiveTransferFile.Status.CANCELLED -> ""
                ActiveTransferFile.Status.FAILED -> ""
                ActiveTransferFile.Status.WAITING, ActiveTransferFile.Status.TRANSFERRING -> ""
            }
            status.visibility = if (file.status == ActiveTransferFile.Status.WAITING || file.status == ActiveTransferFile.Status.TRANSFERRING || file.status == ActiveTransferFile.Status.FAILED || file.status == ActiveTransferFile.Status.CANCELLED) View.GONE else View.VISIBLE
            progress.progress = if (file.totalBytes > 0) ((file.receivedBytes * 100L) / file.totalBytes).toInt().coerceIn(0, 100) else 0
            progressLabel.text = if (file.status == ActiveTransferFile.Status.FAILED) {
                itemView.context.getString(R.string.transfer_failed)
            } else if (file.status == ActiveTransferFile.Status.CANCELLED) {
                itemView.context.getString(R.string.transfer_cancelled)
            } else if (file.totalBytes >= 0) {
                itemView.context.getString(R.string.transfer_progress_bytes, formatBytes(file.receivedBytes.coerceAtLeast(0L)), formatBytes(file.totalBytes))
            } else {
                itemView.context.getString(R.string.transfer_progress_bytes_unknown, formatBytes(file.receivedBytes.coerceAtLeast(0L)))
            }
            val canCancelThisFile = file.direction == ActiveTransferFile.Direction.INCOMING &&
                (file.status == ActiveTransferFile.Status.WAITING || file.status == ActiveTransferFile.Status.TRANSFERRING)
            cancel.visibility = if (canCancelThisFile) View.VISIBLE else View.GONE
            cancel.setText(R.string.cancel_file)
            cancel.setOnClickListener { onCancelFile(file.sessionId, file.fileId) }
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_FILE = 1

        private fun formatBytes(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
