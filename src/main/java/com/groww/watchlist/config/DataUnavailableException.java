package com.groww.watchlist.config;

/** No live, cached or demo data could be produced for a symbol. Maps to HTTP 502. */
public class DataUnavailableException extends RuntimeException {
    public DataUnavailableException(String symbol) {
        super("Market data for " + symbol + " is temporarily unavailable. Please try again shortly.");
    }
}
