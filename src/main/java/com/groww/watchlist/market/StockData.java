package com.groww.watchlist.market;

import java.time.Instant;

/**
 * Everything we know about one symbol from a single upstream call: the
 * current quote plus up to a year of daily closes. Fetching both at once
 * (one HTTP call per symbol instead of three) is the main reason the
 * dashboard is fast.
 *
 * @param days   local exchange calendar day (epoch day) of each close, used to
 *               align symbols from different exchanges by DATE, not by index
 * @param closes daily closing prices, oldest first, same length as {@code days}
 */
public record StockData(String symbol, String name, String currency, String exchange,
                        double price, double previousClose, double dayHigh, double dayLow,
                        double high52, double low52, long volume,
                        long[] days, double[] closes,
                        String source, boolean stale, Instant fetchedAt) {

    public static final String LIVE = "yahoo";
    public static final String DEMO = "demo";

    public boolean demo() {
        return DEMO.equals(source);
    }

    public StockData asStale() {
        return new StockData(symbol, name, currency, exchange, price, previousClose, dayHigh, dayLow,
                high52, low52, volume, days, closes, source, true, fetchedAt);
    }
}
