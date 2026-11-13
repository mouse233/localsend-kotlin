package io.github.mouse233.localsendkotlin.security

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigInteger
import java.net.InetAddress
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/** Exercises the production persistence path with Android's own PKCS12 provider, including API 21. */
@RunWith(AndroidJUnit4::class)
class TlsIdentityInstrumentedTest {
    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var context: IdentityTestContext
    private val keyStoreFile: File get() = File(context.filesDir, "localsend-identity.p12")
    private val preferences: SharedPreferences
        get() = context.getSharedPreferences("localsend_identity", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        val namespace = "tls-identity-test-${UUID.randomUUID()}"
        val directory = File(targetContext.cacheDir, namespace)
        assertTrue(directory.mkdirs())
        context = IdentityTestContext(targetContext, directory, namespace)
    }

    @After
    fun tearDown() {
        if (::context.isInitialized) {
            preferences.edit().clear().commit()
            context.filesDir.deleteRecursively()
        }
    }

    @Test
    fun newlyCreatedIdentityRetainsKeyCertificatePasswordAndFingerprintAfterReload() {
        assertFalse(keyStoreFile.exists())
        val created = TlsIdentity(context)
        val password = requireNotNull(preferences.getString("key_store_password", null))
        assertTrue(password.isNotEmpty())
        val persisted = readIdentity(password)
        assertEquals(created.fingerprint, TlsIdentity.certificateFingerprint(persisted.second))
        val originalBytes = keyStoreFile.readBytes()

        // A new wrapper also catches accidental reliance on the original Context instance.
        val reloaded = TlsIdentity(context.reopen())
        val afterReload = readIdentity(password)
        assertEquals(password, preferences.getString("key_store_password", null))
        assertArrayEquals(originalBytes, keyStoreFile.readBytes())
        assertArrayEquals(persisted.first.encoded, afterReload.first.encoded)
        assertArrayEquals(persisted.second.encoded, afterReload.second.encoded)
        assertEquals(created.fingerprint, reloaded.fingerprint)
        assertMutualTlsWorks(created, reloaded)
    }

    @Test
    fun identityWrittenWithLegacySetKeyEntryIsLoadedWithoutReplacement() {
        val password = UUID.randomUUID().toString()
        assertTrue(preferences.edit().putString("key_store_password", password).commit())
        val expected = writeLegacyIdentity(password)
        val originalBytes = keyStoreFile.readBytes()

        val loaded = TlsIdentity(context)
        val reloaded = TlsIdentity(context.reopen())
        val persisted = readIdentity(password)
        assertEquals(password, preferences.getString("key_store_password", null))
        assertArrayEquals(originalBytes, keyStoreFile.readBytes())
        assertArrayEquals(expected.first.encoded, persisted.first.encoded)
        assertArrayEquals(expected.second.encoded, persisted.second.encoded)
        assertEquals(TlsIdentity.certificateFingerprint(expected.second), loaded.fingerprint)
        assertEquals(loaded.fingerprint, reloaded.fingerprint)
        assertMutualTlsWorks(loaded, reloaded)
    }

    @Test
    fun reloadedIdentityAcceptsMatchingFingerprintAndRejectsMismatch() {
        val created = TlsIdentity(context)
        val reloaded = TlsIdentity(context.reopen())
        val password = requireNotNull(preferences.getString("key_store_password", null))
        val certificate = readIdentity(password).second

        reloaded.trustManagerFor(created.fingerprint).checkServerTrusted(arrayOf(certificate), "RSA")
        val mismatch = if (created.fingerprint.startsWith("0")) "1" else "0"
        val wrongFingerprint = mismatch + created.fingerprint.substring(1)
        assertThrows(CertificateException::class.java) {
            reloaded.trustManagerFor(wrongFingerprint).checkServerTrusted(arrayOf(certificate), "RSA")
        }
    }

    private fun readIdentity(password: String): Pair<PrivateKey, X509Certificate> {
        val store = KeyStore.getInstance("PKCS12")
        keyStoreFile.inputStream().use { store.load(it, password.toCharArray()) }
        assertTrue(store.isKeyEntry("localsend"))
        val chain = requireNotNull(store.getCertificateChain("localsend"))
        assertEquals(1, chain.size)
        val certificate = chain.single() as X509Certificate
        certificate.checkValidity()
        certificate.verify(certificate.publicKey)
        val key = store.getKey("localsend", password.toCharArray()) as PrivateKey
        assertEquals(certificate.publicKey.algorithm, key.algorithm)
        return key to certificate
    }

    /** Deliberately uses the pre-PR writer to model an existing installation's identity. */
    private fun writeLegacyIdentity(password: String): Pair<PrivateKey, X509Certificate> {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val subject = X500Name("CN=Legacy LocalSend identity")
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(
            subject, BigInteger(160, SecureRandom()), Date(now - 60_000),
            Date(now + TimeUnit.DAYS.toMillis(365)), subject, keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val certificate = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        val store = KeyStore.getInstance("PKCS12")
        store.load(null, password.toCharArray())
        store.setKeyEntry("localsend", keyPair.private, password.toCharArray(), arrayOf(certificate))
        keyStoreFile.outputStream().use { store.store(it, password.toCharArray()) }
        return keyPair.private to certificate
    }

    /** A real mutual handshake proves the production key managers can use both retained keys. */
    private fun assertMutualTlsWorks(serverIdentity: TlsIdentity, clientIdentity: TlsIdentity) {
        val executor = Executors.newSingleThreadExecutor()
        val server = serverIdentity.createSslContext().serverSocketFactory.createServerSocket(
            0, 1, InetAddress.getByName("127.0.0.1")
        ) as SSLServerSocket
        server.soTimeout = 10_000
        server.needClientAuth = true
        try {
            val receivedFingerprint = executor.submit<String> {
                (server.accept() as SSLSocket).use { socket ->
                    socket.soTimeout = 10_000
                    socket.startHandshake()
                    val fingerprint = TlsIdentity.certificateFingerprint(socket.session.peerCertificates.single())
                    // Keep both peers alive until the handshake and application data finish.
                    assertEquals(42, socket.inputStream.read())
                    socket.outputStream.write(43)
                    socket.outputStream.flush()
                    fingerprint
                }
            }
            val clientContext = clientIdentity.createSslContext(serverIdentity.fingerprint)
            (clientContext.socketFactory.createSocket() as SSLSocket).use { socket ->
                socket.connect(java.net.InetSocketAddress("127.0.0.1", server.localPort), 10_000)
                socket.soTimeout = 10_000
                socket.startHandshake()
                assertEquals(serverIdentity.fingerprint,
                    TlsIdentity.certificateFingerprint(socket.session.peerCertificates.single()))
                socket.outputStream.write(42)
                socket.outputStream.flush()
                assertEquals(43, socket.inputStream.read())
            }
            assertEquals(clientIdentity.fingerprint, receivedFingerprint.get(15, TimeUnit.SECONDS))
        } finally {
            server.close()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS))
        }
    }

    /** Prevent tests from reading or replacing the app user's real identity and password. */
    private class IdentityTestContext(
        base: Context,
        private val directory: File,
        private val namespace: String
    ) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = directory
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences("$namespace-$name", mode)

        fun reopen(): IdentityTestContext = IdentityTestContext(baseContext, directory, namespace)
    }
}
