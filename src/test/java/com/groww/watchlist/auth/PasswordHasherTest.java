package com.groww.watchlist.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    private final PasswordHasher h = new PasswordHasher(1000);

    @Test
    void roundTripAndWrongPassword() {
        String stored = h.hash("correct-horse-9");
        assertTrue(h.verify("correct-horse-9", stored));
        assertFalse(h.verify("correct-horse-8", stored));
    }

    @Test
    void saltsDifferPerHashAndPlaintextIsNotStored() {
        String a = h.hash("same-password-1"), b = h.hash("same-password-1");
        assertNotEquals(a, b);
        assertFalse(a.contains("same-password-1"));
        assertTrue(a.startsWith("pbkdf2_sha256$1000$"));
    }

    @Test
    void malformedStoredValuesNeverVerify() {
        assertFalse(h.verify("x", "garbage"));
        assertFalse(h.verify("x", "pbkdf2_sha256$abc$AA$BB"));
        assertFalse(h.verify("x", "md5$1$AA$BB"));
        assertFalse(h.verify("x", ""));
    }
}
