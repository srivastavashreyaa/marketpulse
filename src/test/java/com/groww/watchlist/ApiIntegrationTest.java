package com.groww.watchlist;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

    private static final String USER = "test-user-0001";

    @Autowired
    MockMvc mvc;

    @Test
    void addListDetailAndRemoveFlow() throws Exception {
        mvc.perform(post("/api/watchlist").header("X-User-Id", USER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"tcs.ns\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.symbol").value("TCS.NS"));

        mvc.perform(get("/api/dashboard").header("X-User-Id", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].symbol").value("TCS.NS"))
                .andExpect(jsonPath("$.indices.length()").value(5))
                .andExpect(jsonPath("$.digest.headline").isNotEmpty());

        mvc.perform(get("/api/stocks/TCS.NS").header("X-User-Id", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inWatchlist").value(true))
                .andExpect(jsonPath("$.backtest.daysTested").isNumber());

        mvc.perform(post("/api/watchlist/seen").header("X-User-Id", USER)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/watchlist/TCS.NS").header("X-User-Id", USER)).andExpect(status().isNoContent());
        mvc.perform(get("/api/dashboard").header("X-User-Id", USER)).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void rejectsBadInput() throws Exception {
        mvc.perform(post("/api/watchlist").header("X-User-Id", USER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"bad symbol!\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/watchlist").header("X-User-Id", USER).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/dashboard").header("X-User-Id", "x")).andExpect(status().isBadRequest());
    }

    @Test
    void servesTheDashboardPage() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
    }
}
