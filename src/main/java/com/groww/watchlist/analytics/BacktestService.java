package com.groww.watchlist.analytics;

import com.groww.watchlist.dto.BacktestView;
import com.groww.watchlist.dto.BacktestView.ReturnPoint;
import com.groww.watchlist.market.StockData;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Replays the alert rule over up to a year of history and reports how it would have behaved.
 *
 * <p><b>No look-ahead:</b> each day is judged against the volatility of the 30 sessions
 * BEFORE it, exactly what the live rule would have known at the time. We then measure
 * what happened on the next day (continuation) and compare it with the baseline rate
 * across all days, so the numbers say whether a flagged move carried information.
 */
@org.springframework.stereotype.Service
public class BacktestService {

    public static final int TRAILING_WINDOW = 30;
    private static final int CHART_POINTS = 120;

    public BacktestView run(StockData d) {
        double[] r = Stats.returnsPct(d.closes());
        int firstTestable = TRAILING_WINDOW;
        if (r.length <= firstTestable + 5) {
            return new BacktestView(d.symbol(), 0, 0, 0, 0, 0, null, 0, null, 0, "insufficient",
                    "Not enough price history yet to backtest this symbol.", List.of());
        }

        int flagged = 0, continued = 0, withNext = 0;
        double sumAbs = 0, maxAbs = 0, sumNext = 0;
        List<ReturnPoint> series = new ArrayList<>();
        for (int i = firstTestable; i < r.length; i++) {
            double sigma = Stats.stdDev(java.util.Arrays.copyOfRange(r, i - TRAILING_WINDOW, i));
            double threshold = Math.max(SignalService.MIN_THRESHOLD_PCT, SignalService.K * sigma);
            boolean hit = Math.abs(r[i]) >= threshold;
            if (hit) {
                flagged++;
                sumAbs += Math.abs(r[i]);
                maxAbs = Math.max(maxAbs, Math.abs(r[i]));
                if (i + 1 < r.length) {
                    double directional = Math.signum(r[i]) * r[i + 1];
                    withNext++;
                    sumNext += directional;
                    if (directional > 0) continued++;
                }
            }
            // r[i] is the return INTO bar i+1
            int bar = i + 1;
            series.add(new ReturnPoint(LocalDate.ofEpochDay(d.days()[bar]).toString(),
                    Stats.round(r[i], 2), Stats.round(threshold, 2), hit));
        }

        int tested = r.length - firstTestable;
        double baseline = baselineContinuation(r);
        double flagRate = flagged * 100.0 / tested;
        Double contRate = withNext == 0 ? null : Stats.round(continued * 100.0 / withNext, 1);
        Double avgNext = withNext == 0 ? null : Stats.round(sumNext / withNext, 2);

        double sigmaNow = Stats.stdDev(Stats.tail(r, TRAILING_WINDOW));
        double currentThreshold = Math.max(SignalService.MIN_THRESHOLD_PCT, SignalService.K * sigmaNow);

        String verdict = flagged == 0 ? "quiet" : flagRate > 20 ? "noisy" : flagRate < 6 ? "selective" : "balanced";
        List<ReturnPoint> chart = series.size() > CHART_POINTS ? series.subList(series.size() - CHART_POINTS, series.size()) : series;
        return new BacktestView(d.symbol(), tested, flagged, Stats.round(flagRate, 1),
                Stats.round(flagged == 0 ? 0 : sumAbs / flagged, 2), Stats.round(maxAbs, 2),
                contRate, Stats.round(baseline, 1), avgNext, Stats.round(currentThreshold, 2),
                verdict, note(d.symbol(), flagged, tested, flagRate, contRate, baseline, withNext), chart);
    }

    /** How often does the next day follow today's direction, across ALL days (the benchmark). */
    private double baselineContinuation(double[] r) {
        int same = 0, n = 0;
        for (int i = 0; i + 1 < r.length; i++) {
            if (r[i] == 0) continue;
            n++;
            if (Math.signum(r[i]) * r[i + 1] > 0) same++;
        }
        return n == 0 ? 0 : same * 100.0 / n;
    }

    private String note(String symbol, int flagged, int tested, double flagRate, Double cont, double baseline, int withNext) {
        if (flagged == 0) {
            return String.format(Locale.ROOT,
                    "Over %d trading days this rule would never have fired for %s - a very quiet stock.", tested, symbol);
        }
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT,
                "Over %d trading days the rule would have flagged %s %d time%s (%.0f%% of days).",
                tested, symbol, flagged, flagged == 1 ? "" : "s", flagRate));
        if (cont != null) {
            sb.append(String.format(Locale.ROOT,
                    " After a flagged day the move continued the next day %.0f%% of the time vs %.0f%% on an average day.",
                    cont, baseline));
        }
        if (withNext < 8) sb.append(" Small sample - treat this as indicative only.");
        return sb.toString();
    }
}
