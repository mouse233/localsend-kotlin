package io.github.mouse233.localsendkotlin.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.mouse233.localsendkotlin.R
import io.github.mouse233.localsendkotlin.model.ReceivedFile

class ReceivedFileAdapter(private val onOpenFile: (ReceivedFile) -> Unit) : RecyclerView.Adapter<ReceivedFileAdapter.ViewHolder>() {
    private val files = mutableListOf<ReceivedFile>()

    fun submitFiles(newFiles: List<ReceivedFile>) {
        files.clear()
        files.addAll(newFiles)
        notifyDataSetChanged()
    }

    fun addFile(file: ReceivedFile) {
        files.removeAll { it.uri == file.uri }
        files.add(0, file)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_received_file, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(files[position], onOpenFile)
    override fun getItemCount(): Int = files.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.received_file_name)
        private val open: Button = itemView.findViewById(R.id.open_received_file_button)

        fun bind(file: ReceivedFile, onOpenFile: (ReceivedFile) -> Unit) {
            name.text = file.displayName
            itemView.setOnClickListener { onOpenFile(file) }
            open.setOnClickListener { onOpenFile(file) }
        }
    }
}
