package com.groww.watchlist.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class YahooProviderParseTest {

    private final YahooProvider yahoo = new YahooProvider();
    private final ObjectMapper mapper = new ObjectMapper();

    private static final long DAY = 86_400L;
    private static final long T0 = 1_760_000_000L; // arbitrary, UTC offset 0 below

    private String json(long marketTime) {
        return "{\"chart\":{\"error\":null,\"result\":[{\"meta\":{\"currency\":\"INR\",\"longName\":\"Test Co\",\"gmtoffset\":0," +
                "\"regularMarketPrice\":110.5,\"regularMarketTime\":" + marketTime + ",\"chartPreviousClose\":50," +
                "\"fiftyTwoWeekHigh\":120,\"fiftyTwoWeekLow\":80,\"regularMarketVolume\":12345}," +
                "\"timestamp\":[" + T0 + "," + (T0 + DAY) + "," + (T0 + 2 * DAY) + "," + (T0 + 3 * DAY) + "]," +
                "\"indicators\":{\"quote\":[{\"close\":[100,null,105,110.2]}]}}]}}";
    }

    @Test
    void usesPreviousBarAsPreviousCloseWhenLastBarIsToday() throws Exception {
        FetchResult r = yahoo.parse("TEST", mapper.readTree(json(T0 + 3 * DAY)));
        assertThat(r.status()).isEqualTo(FetchResult.Status.OK);
        StockData d = r.data();
        assertThat(d.closes()).hasSize(3);                 // the null close is dropped
        assertThat(d.previousClose()).isEqualTo(105.0);    // NOT chartPreviousClose (50)
        assertThat(d.price()).isEqualTo(110.5);
        assertThat(d.closes()[2]).isEqualTo(110.5);        // live price replaces the partial bar
        assertThat(d.name()).isEqualTo("Test Co");
        assertThat(d.high52()).isEqualTo(120.0);
    }

    @Test
    void usesLastBarAsPreviousCloseWhenMarketIsClosed() throws Exception {
        StockData d = yahoo.parse("TEST", mapper.readTree(json(T0 + 10 * DAY))).data();
        assertThat(d.previousClose()).isEqualTo(110.2);
    }

    @Test
    void unknownTickerIsNotFoundNotUnavailable() throws Exception {
        String bad = "{\"chart\":{\"result\":null,\"error\":{\"code\":\"Not Found\",\"description\":\"No data found\"}}}";
        assertThat(yahoo.parse("NOPE", mapper.readTree(bad)).status()).isEqualTo(FetchResult.Status.NOT_FOUND);
    }
}
