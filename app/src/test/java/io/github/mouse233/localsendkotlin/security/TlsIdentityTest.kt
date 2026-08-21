package io.github.mouse233.localsendkotlin.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TlsIdentityTest {
    @Test
    fun fingerprintsMatchIgnoresCaseAndWhitespace() {
        assertTrue(TlsIdentity.fingerprintsMatch("  AbCd  ", "abcd"))
    }

    @Test
    fun fingerprintsMatchRejectsDifferentValues() {
        assertFalse(TlsIdentity.fingerprintsMatch("abcd", "abce"))
    }
}
