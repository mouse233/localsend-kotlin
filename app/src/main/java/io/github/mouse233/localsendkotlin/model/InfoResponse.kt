package io.github.mouse233.localsendkotlin.model

/** The device information response specified by LocalSend Protocol v2.2 section 6.1. */
data class InfoResponse(
    val alias: String,
    val version: String,
    val deviceModel: String?,
    val deviceType: String?,
    val fingerprint: String,
    val download: Boolean
) {
    companion object {
        fun from(device: DeviceInfo): InfoResponse = InfoResponse(
            alias = device.alias,
            version = device.version,
            deviceModel = device.deviceModel,
            deviceType = device.deviceType,
            fingerprint = device.fingerprint,
            download = device.download
        )
    }
}
