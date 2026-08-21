package io.github.mouse233.localsendkotlin.discovery

import io.github.mouse233.localsendkotlin.model.RemoteDevice
import io.github.mouse233.localsendkotlin.model.ReceivedFile

/** Boundary between UDP/HTTP discovery and the UI layer. */
interface DiscoveryListener {
    fun onDevicesChanged(devices: List<RemoteDevice>)
    fun onDiscoveryError(message: String)
    fun onFileReceived(file: ReceivedFile)
}
