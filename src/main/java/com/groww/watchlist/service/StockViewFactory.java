package com.groww.watchlist.service;

import com.groww.watchlist.analytics.SignalService;
import com.groww.watchlist.analytics.Stats;
import com.groww.watchlist.dto.StockView;
import com.groww.watchlist.market.StockData;
import com.groww.watchlist.model.LastSeenState;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;

/** Turns raw market data + the user's last-seen state into the view the UI renders. */
@Component
public class StockViewFactory {

    private static final int SPARK_POINTS = 30;
    private final SignalService signals;

    public StockViewFactory(SignalService signals) {
        this.signals = signals;
    }

    public StockView build(StockData d, LastSeenState seen, Instant now) {
        SignalService.Signal s = signals.evaluate(d,
                seen == null ? null : seen.getLastSeenPrice(),
                seen == null ? null : seen.getLastSeenAt(), now);

        double[] closes = d.closes();
        int n = closes.length;
        double[] spark = Arrays.copyOfRange(closes, Math.max(0, n - SPARK_POINTS), n);
        for (int i = 0; i < spark.length; i++) spark[i] = Stats.round(spark[i], 2);

        double range = d.high52() - d.low52();
        double rangePos = range > 0 ? Math.min(1, Math.max(0, (d.price() - d.low52()) / range)) : 0.5;

        double sma20 = Stats.lastSma(closes, 20), sma50 = Stats.lastSma(closes, 50);
        String trend = "Sideways";
        if (!Double.isNaN(sma50)) {
            if (d.price() > sma20 && sma20 > sma50) trend = "Uptrend";
            else if (d.price() < sma20 && sma20 < sma50) trend = "Downtrend";
        }
        double ret1m = n > 22 ? (d.price() / closes[n - 22] - 1) * 100 : 0;
        double ret3m = n > 64 ? (d.price() / closes[n - 64] - 1) * 100 : 0;

        return new StockView(d.symbol(), d.name(), d.currency(), d.exchange(), true,
                d.price(), Stats.round(s.dayChangePct(), 2), Stats.round(d.price() - d.previousClose(), 2), d.previousClose(),
                d.dayHigh(), d.dayLow(), d.high52(), d.low52(), Stats.round(rangePos, 3), d.volume(),
                spark,
                s.changeSinceSeenPct() == null ? null : Stats.round(s.changeSinceSeenPct(), 2),
                Stats.round(s.z(), 2), Stats.round(s.dayZ(), 2), Stats.round(s.thresholdPct(), 2), Stats.round(s.sigmaPct(), 2),
                s.meaningful(), s.unusualToday(), s.crossedHigh(), s.crossedLow(), s.reason(),
                Stats.round(s.sigmaPct() * Math.sqrt(252), 1), Stats.round(Stats.rsi(closes, 14), 1), trend,
                Stats.round(ret1m, 1), Stats.round(ret3m, 1),
                d.stale(), d.source());
    }
}
