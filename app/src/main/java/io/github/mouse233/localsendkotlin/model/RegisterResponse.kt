package io.github.mouse233.localsendkotlin.model

/** The protocol deliberately omits port and protocol from a register response. */
data class RegisterResponse(
    val alias: String?,
    val version: String?,
    val deviceModel: String?,
    val deviceType: String?,
    val fingerprint: String?,
    val download: Boolean
)
