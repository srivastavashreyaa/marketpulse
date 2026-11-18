package com.groww.watchlist;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired MockMvc mvc;

    private static final String GUEST = "guest-test-0001";

    private MvcResult signup(String email, String guest) throws Exception {
        return mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse-9\",\"displayName\":\"T\",\"guestId\":\"" + guest + "\"}"))
                .andReturn();
    }

    private static String token(MvcResult r) {
        String h = r.getResponse().getHeader("Set-Cookie");
        assertNotNull(h);
        return h.substring(h.indexOf('=') + 1, h.indexOf(';'));
    }

    @Test
    void signupSetsHardenedCookieAndMigratesGuestList() throws Exception {
        mvc.perform(post("/api/watchlist").header("X-User-Id", GUEST).contentType(MediaType.APPLICATION_JSON)
                .content("{\"symbol\":\"TCS.NS\"}")).andExpect(status().isCreated());

        MvcResult r = signup("mig@example.com", GUEST);
        assertEquals(200, r.getResponse().getStatus());
        String cookie = r.getResponse().getHeader("Set-Cookie");
        assertTrue(cookie.contains("HttpOnly") && cookie.contains("SameSite=Lax"));

        mvc.perform(get("/api/dashboard").header("X-User-Id", "another-device-1").cookie(new jakarta.servlet.http.Cookie("sw_session", token(r))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].symbol").value("TCS.NS"));
        mvc.perform(get("/api/dashboard").header("X-User-Id", GUEST))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void meLogoutAndBadCredentials() throws Exception {
        MvcResult r = signup("flow@example.com", "guest-flow-0001");
        var c = new jakarta.servlet.http.Cookie("sw_session", token(r));
        mvc.perform(get("/api/auth/me").cookie(c)).andExpect(jsonPath("$.authenticated").value(true));
        mvc.perform(post("/api/auth/logout").cookie(c)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").cookie(c)).andExpect(jsonPath("$.authenticated").value(false));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"flow@example.com\",\"password\":\"nope-nope-1\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"flow@example.com\",\"password\":\"correct-horse-9\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void guestCannotUseAccountNamespace() throws Exception {
        mvc.perform(get("/api/dashboard").header("X-User-Id", "acct-00000001")).andExpect(status().isBadRequest());
    }
}
