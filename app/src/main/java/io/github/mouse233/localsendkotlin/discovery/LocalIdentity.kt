package io.github.mouse233.localsendkotlin.discovery

import android.content.Context
import android.os.Build
import io.github.mouse233.localsendkotlin.model.DeviceInfo
import io.github.mouse233.localsendkotlin.protocol.LocalSendProtocol
import io.github.mouse233.localsendkotlin.security.TlsIdentity
import io.github.mouse233.localsendkotlin.settings.AppSettings

class LocalIdentity(context: Context) {
    private val appContext = context.applicationContext
    val tlsIdentity = TlsIdentity(appContext)
    private val settings = AppSettings(appContext)

    fun deviceInfo(): DeviceInfo = DeviceInfo(
        alias = settings.deviceName(),
        version = LocalSendProtocol.VERSION,
        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        deviceType = "mobile",
        fingerprint = tlsIdentity.fingerprint,
        port = LocalSendProtocol.DEFAULT_PORT,
        protocol = "https",
        download = false
    )
}
