package io.github.mouse233.localsendkotlin.model

/**
 * Device JSON received from the network (multicast, register, prepare-upload).
 *
 * Gson does not call Kotlin constructors, so a missing field would otherwise become
 * null inside a non-null [DeviceInfo] property and fail later, often on the main
 * thread. Every field is nullable here and validated once by [toDeviceInfo].
 */
data class DeviceInfoPayload(
    val alias: String?,
    val version: String?,
    val deviceModel: String?,
    val deviceType: String?,
    val fingerprint: String?,
    val port: Int?,
    val protocol: String?,
    val download: Boolean?,
    val announce: Boolean?
) {
    /** Returns null when a field required by the LocalSend v2 protocol is missing. */
    fun toDeviceInfo(): DeviceInfo? = DeviceInfo(
        alias = alias ?: return null,
        version = version ?: return null,
        deviceModel = deviceModel,
        deviceType = deviceType,
        fingerprint = fingerprint ?: return null,
        port = port ?: return null,
        protocol = protocol ?: return null,
        download = download ?: false,
        announce = announce
    )
}
