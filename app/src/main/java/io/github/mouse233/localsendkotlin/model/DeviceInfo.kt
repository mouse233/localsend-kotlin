package io.github.mouse233.localsendkotlin.model

/** JSON payload used by LocalSend v2 discovery and registration. */
data class DeviceInfo(
    val alias: String,
    val version: String,
    val deviceModel: String?,
    val deviceType: String?,
    val fingerprint: String,
    val port: Int,
    val protocol: String,
    val download: Boolean = false,
    val announce: Boolean? = null
)
