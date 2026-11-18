package com.groww.watchlist.config;

/** Client sent something we can't accept (bad symbol format, full watchlist, ...). Maps to HTTP 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
