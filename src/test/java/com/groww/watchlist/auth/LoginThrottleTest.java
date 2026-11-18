package com.groww.watchlist.auth;

import com.groww.watchlist.config.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginThrottleTest {
    @Test
    void locksAfterEightFailuresAndSuccessResets() {
        LoginThrottle t = new LoginThrottle();
        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) { t.check("a@b.co"); t.fail("a@b.co"); }
        assertThrows(TooManyRequestsException.class, () -> t.check("a@b.co"));
        assertDoesNotThrow(() -> t.check("other@b.co"));
        t.success("a@b.co");
        assertDoesNotThrow(() -> t.check("a@b.co"));
    }
}
