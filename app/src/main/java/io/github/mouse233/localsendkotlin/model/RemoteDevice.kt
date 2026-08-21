package io.github.mouse233.localsendkotlin.model

/** A LocalSend peer discovered on the local network. */
data class RemoteDevice(
    val alias: String,
    val deviceModel: String?,
    val deviceType: String?,
    val fingerprint: String,
    val address: String,
    val port: Int,
    val protocol: String,
    val downloadEnabled: Boolean
)
