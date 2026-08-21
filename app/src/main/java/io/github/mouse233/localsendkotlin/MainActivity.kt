package io.github.mouse233.localsendkotlin

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.mouse233.localsendkotlin.discovery.DiscoveryListener
import io.github.mouse233.localsendkotlin.discovery.DiscoveryManager
import io.github.mouse233.localsendkotlin.model.RemoteDevice
import io.github.mouse233.localsendkotlin.ui.DeviceAdapter

/** Entry point for the device-discovery screen. */
class MainActivity : Activity(), DiscoveryListener {

    private lateinit var statusText: TextView
    private val deviceAdapter = DeviceAdapter()
    private lateinit var discoveryManager: DiscoveryManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.discovery_status)
        findViewById<RecyclerView>(R.id.device_list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = deviceAdapter
        }
        discoveryManager = DiscoveryManager(this, this)
        findViewById<android.view.View>(R.id.refresh_button).setOnClickListener {
            statusText.setText(R.string.discovery_refreshing)
            discoveryManager.announce()
        }

        onDevicesChanged(emptyList())
    }

    override fun onStart() {
        super.onStart()
        discoveryManager.start()
    }

    override fun onStop() {
        discoveryManager.stop()
        super.onStop()
    }

    /** Called by the discovery layer on the main thread when peers change. */
    override fun onDevicesChanged(devices: List<RemoteDevice>) {
        deviceAdapter.submitDevices(devices)
        statusText.text = if (devices.isEmpty()) {
            getString(R.string.discovery_scanning)
        } else {
            resources.getQuantityString(R.plurals.device_count, devices.size, devices.size)
        }
    }

    override fun onDiscoveryError(message: String) {
        statusText.text = getString(R.string.discovery_error, message)
    }
}
