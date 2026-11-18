package com.groww.watchlist.analytics;

import java.util.Arrays;

/** Small, dependency-free statistics toolkit. Everything here is a pure function (easy to unit test). */
public final class Stats {

    private Stats() {}

    /** Day-over-day percentage returns; result length is closes.length - 1. */
    public static double[] returnsPct(double[] closes) {
        if (closes.length < 2) return new double[0];
        double[] r = new double[closes.length - 1];
        for (int i = 1; i < closes.length; i++) {
            double prev = closes[i - 1];
            r[i - 1] = prev <= 0 ? 0 : (closes[i] - prev) / prev * 100.0;
        }
        return r;
    }

    public static double mean(double[] a) {
        if (a.length == 0) return 0;
        double s = 0;
        for (double v : a) s += v;
        return s / a.length;
    }

    /** Sample standard deviation (n-1). Returns 0 for fewer than two values. */
    public static double stdDev(double[] a) {
        if (a.length < 2) return 0;
        double m = mean(a), ss = 0;
        for (double v : a) ss += (v - m) * (v - m);
        return Math.sqrt(ss / (a.length - 1));
    }

    public static double[] tail(double[] a, int n) {
        return a.length <= n ? a : Arrays.copyOfRange(a, a.length - n, a.length);
    }

    /** Pearson correlation; 0 when either series is constant or too short. */
    public static double pearson(double[] x, double[] y) {
        int n = Math.min(x.length, y.length);
        if (n < 2) return 0;
        double mx = 0, my = 0;
        for (int i = 0; i < n; i++) { mx += x[i]; my += y[i]; }
        mx /= n; my /= n;
        double cov = 0, vx = 0, vy = 0;
        for (int i = 0; i < n; i++) {
            double dx = x[i] - mx, dy = y[i] - my;
            cov += dx * dy; vx += dx * dx; vy += dy * dy;
        }
        return vx == 0 || vy == 0 ? 0 : cov / Math.sqrt(vx * vy);
    }

    /** Wilder's RSI. Returns a neutral 50 when there isn't enough data. */
    public static double rsi(double[] closes, int period) {
        if (closes.length <= period) return 50;
        double gain = 0, loss = 0;
        for (int i = 1; i <= period; i++) {
            double d = closes[i] - closes[i - 1];
            if (d >= 0) gain += d; else loss -= d;
        }
        gain /= period; loss /= period;
        for (int i = period + 1; i < closes.length; i++) {
            double d = closes[i] - closes[i - 1];
            gain = (gain * (period - 1) + Math.max(d, 0)) / period;
            loss = (loss * (period - 1) + Math.max(-d, 0)) / period;
        }
        if (loss == 0) return gain == 0 ? 50 : 100;
        return 100 - 100 / (1 + gain / loss);
    }

    /** Simple moving average; entries before the window fills are null. */
    public static Double[] sma(double[] closes, int window) {
        Double[] out = new Double[closes.length];
        double sum = 0;
        for (int i = 0; i < closes.length; i++) {
            sum += closes[i];
            if (i >= window) sum -= closes[i - window];
            if (i >= window - 1) out[i] = sum / window;
        }
        return out;
    }

    /** Last value of an SMA, or NaN when there isn't enough history. */
    public static double lastSma(double[] closes, int window) {
        if (closes.length < window) return Double.NaN;
        double s = 0;
        for (int i = closes.length - window; i < closes.length; i++) s += closes[i];
        return s / window;
    }

    public static double round(double v, int places) {
        double f = Math.pow(10, places);
        return Math.round(v * f) / f;
    }
}
