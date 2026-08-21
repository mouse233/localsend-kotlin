package io.github.mouse233.localsendkotlin.security

import android.content.Context
import android.util.Base64
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** A self-signed TLS identity generated once and retained across application launches. */
class TlsIdentity(context: Context) {

    private val appContext = context.applicationContext
    private val password: CharArray = password().toCharArray()
    private val keyStore: KeyStore = loadOrCreateKeyStore()
    private val certificate: X509Certificate = keyStore.getCertificate(ALIAS) as X509Certificate

    val fingerprint: String = certificateFingerprint(certificate)
    val trustManager: X509TrustManager = AcceptAnyTrustManager

    fun createSslContext(): SSLContext {
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, password)
        return SSLContext.getInstance("TLS").apply {
            init(keyManagerFactory.keyManagers, arrayOf<TrustManager>(AcceptAnyTrustManager), SecureRandom())
        }
    }

    private fun loadOrCreateKeyStore(): KeyStore {
        val file = File(appContext.filesDir, KEY_STORE_FILE)
        val store = KeyStore.getInstance("PKCS12")
        if (file.exists()) {
            FileInputStream(file).use { store.load(it, password) }
            if (store.containsAlias(ALIAS)) return store
        }

        store.load(null, password)
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(KEY_SIZE_BITS)
        val keyPair = generator.generateKeyPair()
        val certificate = createCertificate(keyPair.private, keyPair.public)
        store.setKeyEntry(ALIAS, keyPair.private, password, arrayOf(certificate))
        FileOutputStream(file).use { store.store(it, password) }
        return store
    }

    private fun createCertificate(
        privateKey: PrivateKey,
        publicKey: java.security.PublicKey
    ): X509Certificate {
        val now = System.currentTimeMillis()
        val subject = X500Name("CN=LocalSend Kotlin")
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger(160, SecureRandom()),
            Date(now - CLOCK_SKEW_MS),
            Date(now + CERTIFICATE_VALIDITY_MS),
            subject,
            publicKey
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(privateKey)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer)).also {
            it.verify(publicKey)
        }
    }

    private fun password(): String {
        val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.getString(PASSWORD_KEY, null)?.let { return it }
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP).also {
            preferences.edit().putString(PASSWORD_KEY, it).apply()
        }
    }

    companion object {
        fun certificateFingerprint(certificate: java.security.cert.Certificate): String =
            MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
                .joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }

        private const val ALIAS = "localsend"
        private const val KEY_STORE_FILE = "localsend-identity.p12"
        private const val PREFERENCES_NAME = "localsend_identity"
        private const val PASSWORD_KEY = "key_store_password"
        private const val KEY_SIZE_BITS = 2048
        private const val CLOCK_SKEW_MS = 60_000L
        private const val CERTIFICATE_VALIDITY_MS = 3650L * 24 * 60 * 60 * 1000
    }

    private object AcceptAnyTrustManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
