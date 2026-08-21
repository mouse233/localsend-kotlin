package io.github.mouse233.localsendkotlin.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import io.github.mouse233.localsendkotlin.R
import io.github.mouse233.localsendkotlin.model.RemoteDevice

class DeviceAdapter(private val onDeviceClick: (RemoteDevice) -> Unit) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    private val devices = mutableListOf<RemoteDevice>()

    fun submitDevices(newDevices: List<RemoteDevice>) {
        val oldDevices = devices.toList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldDevices.size
            override fun getNewListSize(): Int = newDevices.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldDevices[oldItemPosition].fingerprint == newDevices[newItemPosition].fingerprint
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldDevices[oldItemPosition] == newDevices[newItemPosition]
        })
        devices.clear()
        devices.addAll(newDevices)
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position], onDeviceClick)
    }

    override fun getItemCount(): Int = devices.size

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val alias: TextView = itemView.findViewById(R.id.device_alias)
        private val details: TextView = itemView.findViewById(R.id.device_details)

        fun bind(device: RemoteDevice, onDeviceClick: (RemoteDevice) -> Unit) {
            alias.text = device.alias
            val deviceName = device.deviceModel ?: device.deviceType ?: "Unknown device"
            details.text = itemView.context.getString(
                R.string.device_details_format,
                deviceName,
                device.address,
                device.port
            )
            itemView.setOnClickListener { onDeviceClick(device) }
        }
    }
}
