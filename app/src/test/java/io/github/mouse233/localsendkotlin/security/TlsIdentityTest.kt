package io.github.mouse233.localsendkotlin.security

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class TlsIdentityTest {
    @Test
    fun fingerprintsMatchIgnoresCaseAndWhitespace() {
        assertTrue(TlsIdentity.fingerprintsMatch("  AbCd  ", "abcd"))
    }

    @Test
    fun fingerprintsMatchRejectsDifferentValues() {
        assertFalse(TlsIdentity.fingerprintsMatch("abcd", "abce"))
    }

    /**
     * Reproduces the NanoHTTPD 2.3.1 server flow that broke receiving on Android 5.1:
     * the accept loop calls SSLSocket.getInputStream() and only afterwards registers
     * a handshakeCompletedListener, so the listener-based fingerprint capture could
     * miss the handshake entirely. The fix reads the peer certificate directly after
     * an explicit (idempotent) startHandshake() on the worker thread. This test proves
     * that pattern yields the client certificate's fingerprint.
     */
    @Test
    fun explicitHandshakeExposesPeerFingerprint() {
        val server = identity("server")
        val client = identity("client")
        val password = "test-pass".toCharArray()

        val serverSocket = serverSocket(server.first, password)
        try {
            val expected = TlsIdentity.certificateFingerprint(client.second)
            val actual = AtomicReference<String?>()
            val acceptor = Thread {
                val socket = serverSocket.accept() as SSLSocket
                try {
                    socket.getInputStream().read() // NanoHTTPD touches the stream first
                    socket.startHandshake()        // the fix: explicit, idempotent handshake
                    val peer = socket.session.peerCertificates.firstOrNull()
                        ?: throw IllegalStateException("Peer did not present a certificate")
                    actual.set(TlsIdentity.certificateFingerprint(peer))
                } finally {
                    socket.close()
                }
            }
            acceptor.start()

            val clientSocket = clientSocket(serverSocket.localPort, client.first, password)
            clientSocket.startHandshake()
            clientSocket.getOutputStream().write(0)
            clientSocket.close()
            acceptor.join(10_000)

            assertNotNull("Server failed to capture the peer fingerprint", actual.get())
            assertEquals(expected, actual.get())
        } finally {
            serverSocket.close()
        }
    }

    /** Generates a self-signed key pair and returns (KeyStore with private key, certificate). */
    private fun identity(cn: String): Pair<KeyStore, X509Certificate> {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(
            X500Name("CN=$cn"),
            BigInteger(160, SecureRandom()),
            Date(now - 60_000),
            Date(now + 3_650L * 24 * 60 * 60 * 1000),
            X500Name("CN=$cn"),
            keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val certificate = JcaX509CertificateConverter().getCertificate(builder.build(signer))
        val store = KeyStore.getInstance("PKCS12")
        store.load(null, "test-pass".toCharArray())
        store.setKeyEntry("id", keyPair.private, "test-pass".toCharArray(), arrayOf(certificate))
        return store to certificate
    }

    private fun serverSocket(store: KeyStore, password: CharArray): SSLServerSocket {
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(store, password)
        val context = SSLContext.getInstance("TLS")
        context.init(kmf.keyManagers, arrayOf<TrustManager>(acceptAll()), SecureRandom())
        return (context.serverSocketFactory.createServerSocket(0) as SSLServerSocket).apply { needClientAuth = true }
    }

    private fun clientSocket(port: Int, store: KeyStore, password: CharArray): SSLSocket {
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(store, password)
        val context = SSLContext.getInstance("TLS")
        context.init(kmf.keyManagers, arrayOf<TrustManager>(acceptAll()), SecureRandom())
        return context.socketFactory.createSocket("127.0.0.1", port) as SSLSocket
    }

    private fun acceptAll(): X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
