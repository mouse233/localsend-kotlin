package io.github.mouse233.localsendkotlin.model

/** A persisted device bookmark. The certificate fingerprint is the stable identity. */
data class FavoriteDevice(
    val fingerprint: String,
    val alias: String,
    val address: String,
    val port: Int,
    val protocol: String,
    val customAlias: Boolean = false
) {
    fun matches(device: RemoteDevice): Boolean = fingerprint.equals(device.fingerprint, ignoreCase = true)

    fun refreshedFrom(device: RemoteDevice): FavoriteDevice = copy(
        alias = if (customAlias) alias else device.alias
    )
}
