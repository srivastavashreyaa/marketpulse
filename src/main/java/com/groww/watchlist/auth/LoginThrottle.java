package com.groww.watchlist.auth;

import com.groww.watchlist.config.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Blocks password guessing: after 8 failures in 15 minutes an email is locked out until the window passes. */
@Component
public class LoginThrottle {

    static final int MAX_FAILURES = 8;
    static final long WINDOW_MS = 15 * 60 * 1000L;

    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    public void check(String key) {
        Deque<Long> q = failures.get(key);
        if (q == null) return;
        synchronized (q) {
            prune(q);
            if (q.size() >= MAX_FAILURES) {
                throw new TooManyRequestsException("Too many failed attempts. Please wait a few minutes and try again.");
            }
        }
    }

    public void fail(String key) {
        if (failures.size() > 20_000) failures.clear(); // memory guard against key flooding
        Deque<Long> q = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            prune(q);
            q.addLast(System.currentTimeMillis());
        }
    }

    public void success(String key) {
        failures.remove(key);
    }

    private void prune(Deque<Long> q) {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        while (!q.isEmpty() && q.peekFirst() < cutoff) q.pollFirst();
    }
}
