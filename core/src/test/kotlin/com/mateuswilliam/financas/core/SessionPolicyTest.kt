package com.mateuswilliam.financas.core

import org.junit.Assert.*
import org.junit.Test

class SessionPolicyTest {
    @Test fun coldStartRequiresAuthenticationOnlyWhenEnabled() {
        assertTrue(SessionPolicy.requiresAuthentication(true, false, null, 100, false))
        assertFalse(SessionPolicy.requiresAuthentication(false, false, null, 100, true))
    }
    @Test fun foregroundRotationDoesNotInvalidateAnAuthenticatedSession() {
        assertFalse(SessionPolicy.requiresAuthentication(true, true, null, 100, false))
    }
    @Test fun backgroundTimeoutIsThirtySeconds() {
        assertFalse(SessionPolicy.requiresAuthentication(true, true, 100, 30099, false))
        assertTrue(SessionPolicy.requiresAuthentication(true, true, 100, 30100, false))
    }
    @Test fun deviceLockRequiresAuthenticationEvenBeforeTimeout() {
        assertTrue(SessionPolicy.requiresAuthentication(true, true, 100, 101, true))
    }
}
