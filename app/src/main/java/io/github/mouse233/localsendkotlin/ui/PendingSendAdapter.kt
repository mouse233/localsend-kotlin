package io.github.mouse233.localsendkotlin.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.github.mouse233.localsendkotlin.R
import io.github.mouse233.localsendkotlin.model.PendingSendFile

class PendingSendAdapter(private val onRemove: (PendingSendFile) -> Unit) : RecyclerView.Adapter<PendingSendAdapter.ViewHolder>() {
    private val files = mutableListOf<PendingSendFile>()

    fun submitFiles(newFiles: List<PendingSendFile>) {
        val oldFiles = files.toList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldFiles.size
            override fun getNewListSize() = newFiles.size
            override fun areItemsTheSame(oldPosition: Int, newPosition: Int) = oldFiles[oldPosition].uri == newFiles[newPosition].uri
            override fun areContentsTheSame(oldPosition: Int, newPosition: Int) = oldFiles[oldPosition] == newFiles[newPosition]
        })
        files.clear()
        files.addAll(newFiles)
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_pending_send, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(files[position], onRemove)
    override fun getItemCount() = files.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.pending_send_name)
        private val size: TextView = itemView.findViewById(R.id.pending_send_size)
        private val remove: ImageButton = itemView.findViewById(R.id.pending_send_remove)

        fun bind(file: PendingSendFile, onRemove: (PendingSendFile) -> Unit) {
            name.text = file.displayName
            size.text = if (file.size >= 0) formatBytes(file.size) else itemView.context.getString(R.string.file_size_unknown)
            remove.setOnClickListener { onRemove(file) }
        }

        private fun formatBytes(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
