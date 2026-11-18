package com.groww.watchlist.config;

/** The ticker does not exist according to the upstream provider. Maps to HTTP 404. */
public class UnknownSymbolException extends RuntimeException {
    public UnknownSymbolException(String symbol) {
        super("We couldn't find a stock called \"" + symbol + "\". Check the ticker (NSE stocks end in .NS, e.g. TCS.NS).");
    }
}
