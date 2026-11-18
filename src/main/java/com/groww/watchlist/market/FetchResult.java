package com.groww.watchlist.market;

/**
 * Outcome of asking a provider for a symbol. The distinction between
 * NOT_FOUND ("that ticker does not exist") and UNAVAILABLE ("the provider is
 * down / blocking us") matters: only UNAVAILABLE may fall back to cached or
 * demo data, otherwise a typo'd ticker would silently get fake prices.
 */
public record FetchResult(Status status, StockData data) {

    public enum Status { OK, NOT_FOUND, UNAVAILABLE }

    public static FetchResult ok(StockData data) {
        return new FetchResult(Status.OK, data);
    }

    public static FetchResult notFound() {
        return new FetchResult(Status.NOT_FOUND, null);
    }

    public static FetchResult unavailable() {
        return new FetchResult(Status.UNAVAILABLE, null);
    }
}
