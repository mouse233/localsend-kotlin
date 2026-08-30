package io.github.mouse233.localsendkotlin.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.mouse233.localsendkotlin.R
import io.github.mouse233.localsendkotlin.model.InstalledApp
import java.util.Locale

class InstalledAppAdapter(
    private val allApps: List<InstalledApp>,
    private val selectedPackages: MutableSet<String>,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<InstalledAppAdapter.ViewHolder>() {
    private var visibleApps = allApps

    fun filter(query: String) {
        val normalized = query.trim().lowercase(Locale.getDefault())
        visibleApps = if (normalized.isBlank()) {
            allApps
        } else {
            allApps.filter { app ->
                app.label.lowercase(Locale.getDefault()).contains(normalized) ||
                    app.packageName.lowercase(Locale.getDefault()).contains(normalized)
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        LayoutInflater.from(parent.context)
            .inflate(R.layout.item_installed_app, parent, false)
            .also(ThemeColors::apply)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(visibleApps[position], selectedPackages, onSelectionChanged)
    }

    override fun getItemCount(): Int = visibleApps.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.installed_app_icon)
        private val name: TextView = itemView.findViewById(R.id.installed_app_name)
        private val packageName: TextView = itemView.findViewById(R.id.installed_app_package)
        private val checkbox: CheckBox = itemView.findViewById(R.id.installed_app_checkbox)

        fun bind(
            app: InstalledApp,
            selectedPackages: MutableSet<String>,
            onSelectionChanged: (Int) -> Unit
        ) {
            icon.setImageDrawable(app.icon ?: itemView.context.getDrawable(android.R.drawable.sym_def_app_icon))
            name.text = app.label
            packageName.text = app.packageName
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = app.packageName in selectedPackages
            checkbox.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedPackages += app.packageName else selectedPackages -= app.packageName
                onSelectionChanged(selectedPackages.size)
            }
            itemView.setOnClickListener { checkbox.isChecked = !checkbox.isChecked }
        }
    }
}
