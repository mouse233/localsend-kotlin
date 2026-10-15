package io.github.mouse233.localsendkotlin.transfer

import com.google.gson.JsonParseException
import io.github.mouse233.localsendkotlin.R
import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

/** Localize common platform failures without changing how the connection is validated. */
internal fun uploadErrorResource(error: Throwable): Int? {
    val causes = generateSequence(error) { it.cause }.take(8).toList()
    return when {
        causes.any { it is SSLException || it is CertificateException } -> R.string.upload_tls_failed
        causes.any { it is SocketTimeoutException } -> R.string.upload_timeout
        causes.any { it is ConnectException || it is UnknownHostException } -> R.string.upload_connection_failed
        causes.any { it is SecurityException } -> R.string.upload_access_denied
        causes.any { it is FileNotFoundException } -> R.string.upload_cannot_read_file
        causes.any { it is JsonParseException } -> R.string.upload_invalid_response
        else -> null
    }
}
