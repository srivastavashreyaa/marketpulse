package com.groww.watchlist.dto;

/** One watchlist row/card: the quote, the adaptive signal, and a few risk/trend indicators. */
public record StockView(
        String symbol, String name, String currency, String exchange,
        boolean available,
        double price, double dayChangePct, double dayChange, double previousClose,
        double dayHigh, double dayLow, double high52, double low52, double rangePos52, long volume,
        double[] spark,
        // adaptive signal
        Double changeSinceSeenPct, double signalZ, double dayZ, double thresholdPct, double sigmaPct,
        boolean meaningful, boolean unusualToday, boolean crossedHigh, boolean crossedLow, String reason,
        // indicators
        double annVolPct, double rsi, String trend, double ret1mPct, double ret3mPct,
        // data quality
        boolean stale, String source) {

    public static StockView unavailable(String symbol, String name) {
        return new StockView(symbol, name == null ? symbol : name, "", "", false, 0, 0, 0, 0, 0, 0, 0, 0, 0.5, 0,
                new double[0], null, 0, 0, 0, 0, false, false, false, false,
                "Price data temporarily unavailable", 0, 50, "n/a", 0, 0, true, "none");
    }
}
