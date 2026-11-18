package com.groww.watchlist.analytics;

import com.groww.watchlist.market.StockData;

import java.time.Instant;

final class TestData {
    private TestData() {}

    /** Closes whose daily returns alternate +pct / -pct, so sigma is about pct. */
    static StockData alternating(String symbol, double pct, int bars, double lastPrice) {
        double[] closes = new double[bars];
        long[] days = new long[bars];
        closes[0] = 100;
        for (int i = 1; i < bars; i++) closes[i] = closes[i - 1] * (1 + (i % 2 == 0 ? pct : -pct) / 100.0);
        for (int i = 0; i < bars; i++) days[i] = 20_000 + i;
        closes[bars - 1] = lastPrice;
        return stock(symbol, closes, days, lastPrice);
    }

    static StockData stock(String symbol, double[] closes, long[] days, double price) {
        return new StockData(symbol, symbol, "USD", "TEST", price, closes[closes.length - 2], price, price,
                1000, 1, 0, days, closes, StockData.LIVE, false, Instant.now());
    }
}
