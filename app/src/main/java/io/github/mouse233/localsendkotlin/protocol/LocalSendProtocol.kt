package io.github.mouse233.localsendkotlin.protocol

/** Constants defined by the LocalSend v2.2 protocol. */
object LocalSendProtocol {
    const val VERSION = "2.0"
    const val DEFAULT_PORT = 53317
    const val MULTICAST_ADDRESS = "224.0.0.167"
    const val REGISTER_PATH = "/api/localsend/v2/register"
    const val PREPARE_UPLOAD_PATH = "/api/localsend/v2/prepare-upload"
    const val UPLOAD_PATH = "/api/localsend/v2/upload"
    const val CANCEL_PATH = "/api/localsend/v2/cancel"
}
