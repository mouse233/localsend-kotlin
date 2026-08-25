package io.github.mouse233.localsendkotlin.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PinAuthenticatorTest {
    @Test
    fun noConfiguredPinAllowsRequest() {
        assertEquals(PinAuthenticator.Result.NOT_REQUIRED, PinAuthenticator.check(null, null, 0))
    }

    @Test
    fun missingPinIsUnauthorizedWithoutIncreasingAttemptCount() {
        assertEquals(PinAuthenticator.Result.INVALID, PinAuthenticator.check("1234", null, 0))
        assertEquals(PinAuthenticator.Result.INVALID, PinAuthenticator.check("1234", null, 2))
    }

    @Test
    fun correctPinIsAcceptedAndCanResetAttempts() {
        assertEquals(PinAuthenticator.Result.ACCEPTED, PinAuthenticator.check("  你好PIN!  ", " 你好PIN! ", 2))
    }

    @Test
    fun wrongPinLocksAfterThreeFailedAttempts() {
        assertEquals(PinAuthenticator.Result.INVALID, PinAuthenticator.check("1234", "0000", 0))
        assertEquals(PinAuthenticator.Result.INVALID, PinAuthenticator.check("1234", "0000", 2))
        assertEquals(PinAuthenticator.Result.TOO_MANY_ATTEMPTS, PinAuthenticator.check("1234", "1234", 3))
    }
}
