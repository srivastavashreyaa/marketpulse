package com.groww.watchlist.dto;

public record AuthStatus(boolean authenticated, String email, String displayName) {
    public static AuthStatus guest() { return new AuthStatus(false, null, null); }
}
