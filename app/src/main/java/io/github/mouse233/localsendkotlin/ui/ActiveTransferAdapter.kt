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

class ActiveTransferAdapter(private val onCancelFile: (String, String) -> Unit) : RecyclerView.Adapter<ActiveTransferAdapter.ViewHolder>() {
    private val files = mutableListOf<ActiveTransferFile>()

    fun submitFiles(newFiles: List<ActiveTransferFile>) {
        val oldFiles = files.toList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldFiles.size
            override fun getNewListSize() = newFiles.size
            override fun areItemsTheSame(oldPosition: Int, newPosition: Int) =
                oldFiles[oldPosition].sessionId == newFiles[newPosition].sessionId && oldFiles[oldPosition].fileId == newFiles[newPosition].fileId
            override fun areContentsTheSame(oldPosition: Int, newPosition: Int) = oldFiles[oldPosition] == newFiles[newPosition]
        })
        files.clear()
        files.addAll(newFiles)
        diff.dispatchUpdatesTo(this)
    }

    fun updateFile(file: ActiveTransferFile) {
        val index = files.indexOfFirst { it.sessionId == file.sessionId && it.fileId == file.fileId }
        if (index < 0) return
        files[index] = file
        notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_active_transfer, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(files[position], onCancelFile)
    override fun getItemCount() = files.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.active_transfer_name)
        private val status: TextView = itemView.findViewById(R.id.active_transfer_status)
        private val progress: ProgressBar = itemView.findViewById(R.id.active_transfer_progress)
        private val cancel: Button = itemView.findViewById(R.id.active_transfer_cancel)

        fun bind(file: ActiveTransferFile, onCancelFile: (String, String) -> Unit) {
            name.text = file.fileName
            status.text = when (file.status) {
                ActiveTransferFile.Status.WAITING -> "等待"
                ActiveTransferFile.Status.TRANSFERRING -> "传输中"
                ActiveTransferFile.Status.COMPLETED -> "完成"
                ActiveTransferFile.Status.CANCELLED -> "已取消"
                ActiveTransferFile.Status.FAILED -> "失败"
            }
            progress.progress = if (file.totalBytes > 0) ((file.receivedBytes * 100L) / file.totalBytes).toInt().coerceIn(0, 100) else 0
            cancel.visibility = if (file.status == ActiveTransferFile.Status.WAITING || file.status == ActiveTransferFile.Status.TRANSFERRING) View.VISIBLE else View.GONE
            cancel.setOnClickListener { onCancelFile(file.sessionId, file.fileId) }
        }
    }
}
