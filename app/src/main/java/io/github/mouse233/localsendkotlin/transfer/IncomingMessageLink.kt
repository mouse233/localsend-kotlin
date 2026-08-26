package io.github.mouse233.localsendkotlin.transfer

/** Detects a single URI message that can be handed to another Android app. */
object IncomingMessageLink {
    private val URI_PATTERN = Regex("^[A-Za-z][A-Za-z0-9+.-]*:[^\\s]+$")

    fun detect(message: String): String? {
        val candidate = message.trim()
        return candidate.takeIf { it.isNotEmpty() && URI_PATTERN.matches(it) }
    }
}
