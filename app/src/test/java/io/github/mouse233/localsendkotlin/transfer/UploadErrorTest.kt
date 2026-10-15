package io.github.mouse233.localsendkotlin.transfer

import io.github.mouse233.localsendkotlin.R
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UploadErrorTest {
    @Test
    fun wrappedCertificateRejectionsKeepTheirOwnLocalizedReason() {
        val handshake = SSLHandshakeException("Handshake failed").apply {
            initCause(CertificateException("Fingerprint mismatch"))
        }
        assertEquals(R.string.upload_tls_failed, uploadErrorResource(IOException(handshake)))
    }

    @Test
    fun platformNetworkAndFileErrorsHaveActionableLocalizedReasons() {
        assertEquals(R.string.upload_timeout, uploadErrorResource(SocketTimeoutException()))
        assertEquals(R.string.upload_connection_failed, uploadErrorResource(ConnectException()))
        assertEquals(R.string.upload_connection_failed, uploadErrorResource(UnknownHostException()))
        assertEquals(R.string.upload_access_denied, uploadErrorResource(SecurityException()))
        assertEquals(R.string.upload_cannot_read_file, uploadErrorResource(FileNotFoundException()))
    }

    @Test
    fun existingApplicationMessagesArePreserved() {
        assertNull(uploadErrorResource(IllegalStateException("An already localized rejection")))
    }
}
