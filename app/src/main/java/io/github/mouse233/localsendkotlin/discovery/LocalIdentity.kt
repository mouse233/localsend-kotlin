package io.github.mouse233.localsendkotlin.discovery

import android.content.Context
import io.github.mouse233.localsendkotlin.model.DeviceInfo
import io.github.mouse233.localsendkotlin.protocol.LocalSendProtocol
import io.github.mouse233.localsendkotlin.security.TlsIdentity
import io.github.mouse233.localsendkotlin.settings.AppSettings

class LocalIdentity(context: Context) {
    private val appContext = context.applicationContext
    /**
     * Loading the PKCS#12 store can generate a certificate on first launch and
     * takes noticeable time on older devices. Keep one process-wide identity
     * and create it only on the worker that actually needs TLS.
     */
    val tlsIdentity: TlsIdentity
        get() = sharedTlsIdentity(appContext)
    private val settings = AppSettings(appContext)

    fun deviceInfo(): DeviceInfo = DeviceInfo(
        alias = settings.deviceName(),
        version = LocalSendProtocol.VERSION,
        deviceModel = settings.deviceModel(),
        deviceType = settings.deviceType(),
        fingerprint = if (settings.encryptionEnabled()) tlsIdentity.fingerprint else settings.plainHttpFingerprint(),
        port = settings.port(),
        protocol = if (settings.encryptionEnabled()) "https" else "http",
        download = false
    )

    private companion object {
        @Volatile private var cachedTlsIdentity: TlsIdentity? = null

        private fun sharedTlsIdentity(context: Context): TlsIdentity = cachedTlsIdentity
            ?: synchronized(this) {
                cachedTlsIdentity ?: TlsIdentity(context.applicationContext).also { cachedTlsIdentity = it }
            }
    }
}
