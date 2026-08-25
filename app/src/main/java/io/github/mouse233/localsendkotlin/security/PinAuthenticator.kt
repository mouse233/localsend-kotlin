package io.github.mouse233.localsendkotlin.security

/** Protocol-level PIN decision shared by the server and its unit tests. */
object PinAuthenticator {
    const val MAX_FAILED_ATTEMPTS = 3

    enum class Result {
        NOT_REQUIRED,
        ACCEPTED,
        INVALID,
        TOO_MANY_ATTEMPTS
    }

    fun check(requiredPin: String?, suppliedPin: String?, failedAttempts: Int): Result {
        val normalizedRequired = requiredPin?.trim()?.takeIf { it.isNotEmpty() }
            ?: return Result.NOT_REQUIRED
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) return Result.TOO_MANY_ATTEMPTS
        if (suppliedPin?.trim() == normalizedRequired) return Result.ACCEPTED
        return Result.INVALID
    }
}
