package com.groww.watchlist.config;

/** Too many failed attempts. Maps to HTTP 429. */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
