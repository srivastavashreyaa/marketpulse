package com.groww.watchlist.analytics;

import com.groww.watchlist.market.StockData;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;

/**
 * Decides whether a move "deserves your attention" for THIS stock.
 *
 * <p>Every stock has its own normal wobble, measured as sigma = the standard
 * deviation of its daily returns over the previous 60 sessions (seeded from real
 * history, so it works from the very first request and survives restarts).
 *
 * <p>The yardstick also scales with how long you've been away. If you last looked
 * 4 trading days ago, a 3% drift is much less surprising than if you looked 5
 * minutes ago, because uncertainty grows with the square root of time:
 * <pre>threshold = K * sigma * sqrt(tradingDaysAway)   (never less than one day)</pre>
 * A change is "meaningful" when it exceeds K=1.5 of those units. {@code z} is the
 * same change expressed as a multiple of the yardstick.
 */
@org.springframework.stereotype.Service
public class SignalService {

    public static final double K = 1.5;
    public static final double MIN_THRESHOLD_PCT = 0.3;
    public static final double DEFAULT_SIGMA_PCT = 1.0;
    public static final int VOL_WINDOW = 60;

    public record Signal(double sigmaPct, double thresholdPct, Double changeSinceSeenPct, double z,
                         double dayChangePct, double dayZ, boolean meaningful,
                         boolean crossedHigh, boolean crossedLow, boolean unusualToday, String reason) {}

    /** Daily sigma (in %) from the previous sessions, excluding the latest (possibly partial) bar. */
    public double dailySigma(double[] closes) {
        double[] r = Stats.returnsPct(closes);
        if (r.length > 1) r = Arrays.copyOf(r, r.length - 1);
        r = Stats.tail(r, VOL_WINDOW);
        return r.length < 10 ? DEFAULT_SIGMA_PCT : Math.max(Stats.stdDev(r), 0.05);
    }

    public double tradingDaysAway(Instant seenAt, Instant now) {
        if (seenAt == null) return 1;
        double calendarDays = Duration.between(seenAt, now).toSeconds() / 86_400.0;
        return Math.min(20, Math.max(1, calendarDays * 5.0 / 7.0));
    }

    public double thresholdPct(double sigmaPct, double tradingDays) {
        return Math.max(MIN_THRESHOLD_PCT, K * sigmaPct * Math.sqrt(tradingDays));
    }

    public Signal evaluate(StockData d, Double lastSeenPrice, Instant lastSeenAt, Instant now) {
        double sigma = dailySigma(d.closes());
        double days = tradingDaysAway(lastSeenAt, now);
        double threshold = thresholdPct(sigma, days);
        double yardstick = threshold / K;

        double dayChange = d.previousClose() > 0 ? (d.price() - d.previousClose()) / d.previousClose() * 100.0 : 0;
        double dayZ = Math.abs(dayChange) / sigma;
        boolean unusualToday = dayZ >= 2.0;

        Double change = null;
        boolean crossedHigh = false, crossedLow = false;
        if (lastSeenPrice != null && lastSeenPrice > 0) {
            change = (d.price() - lastSeenPrice) / lastSeenPrice * 100.0;
            crossedHigh = lastSeenPrice < d.high52() && d.price() >= d.high52() * 0.9995;
            crossedLow = lastSeenPrice > d.low52() && d.price() <= d.low52() * 1.0005;
        }
        double z = change == null ? 0 : Math.abs(change) / yardstick;
        boolean meaningful = change != null && (Math.abs(change) >= threshold || crossedHigh || crossedLow);
        return new Signal(sigma, threshold, change, z, dayChange, dayZ, meaningful, crossedHigh, crossedLow,
                unusualToday, reason(change, threshold, z, crossedHigh, crossedLow, meaningful));
    }

    private String reason(Double change, double threshold, double z, boolean high, boolean low, boolean meaningful) {
        if (change == null) return "Just added - tracking starts now";
        if (high) return String.format(Locale.ROOT, "New 52-week high, %+.2f%% since you last looked", change);
        if (low) return String.format(Locale.ROOT, "New 52-week low, %+.2f%% since you last looked", change);
        if (meaningful) {
            return String.format(Locale.ROOT, "%s %.2f%% since you last looked - %.1fx its normal range (+/-%.1f%%)",
                    change > 0 ? "Up" : "Down", Math.abs(change), z * K, threshold);
        }
        return String.format(Locale.ROOT, "Quiet - %+.2f%% is within its normal +/-%.1f%% range", change, threshold);
    }
}
