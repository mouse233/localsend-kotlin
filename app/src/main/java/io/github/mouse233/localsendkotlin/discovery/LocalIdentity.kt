package io.github.mouse233.localsendkotlin.discovery

import android.content.Context
import android.os.Build
import io.github.mouse233.localsendkotlin.model.DeviceInfo
import io.github.mouse233.localsendkotlin.protocol.LocalSendProtocol
import io.github.mouse233.localsendkotlin.security.TlsIdentity

class LocalIdentity(context: Context) {
    val tlsIdentity = TlsIdentity(context)

    fun deviceInfo(): DeviceInfo = DeviceInfo(
        alias = Build.MODEL.ifBlank { "Android" },
        version = LocalSendProtocol.VERSION,
        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        deviceType = "mobile",
        fingerprint = tlsIdentity.fingerprint,
        port = LocalSendProtocol.DEFAULT_PORT,
        protocol = "https",
        download = false
    )
}
