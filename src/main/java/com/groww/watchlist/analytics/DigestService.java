package com.groww.watchlist.analytics;

import com.groww.watchlist.dto.DigestView;
import com.groww.watchlist.dto.StockView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Writes the one-glance "Market Pulse" summary of the whole watchlist. */
@org.springframework.stereotype.Service
public class DigestService {

    public DigestView build(List<StockView> views, int divergentPairs) {
        List<StockView> live = views.stream().filter(StockView::available).toList();
        if (live.isEmpty()) {
            return new DigestView("Add a few stocks and I'll tell you what actually deserves your attention.",
                    "neutral", List.of(), 0, 0, 0, 0, 0);
        }
        int up = 0, down = 0, flat = 0, meaningful = 0;
        double sum = 0;
        for (StockView v : live) {
            sum += v.dayChangePct();
            if (v.dayChangePct() > 0.05) up++; else if (v.dayChangePct() < -0.05) down++; else flat++;
            if (v.meaningful()) meaningful++;
        }
        double avg = sum / live.size();
        StockView best = live.stream().max(Comparator.comparingDouble(StockView::dayChangePct)).orElseThrow();
        StockView worst = live.stream().min(Comparator.comparingDouble(StockView::dayChangePct)).orElseThrow();

        String headline;
        String tone;
        if (meaningful > 0) {
            StockView top = live.stream().filter(StockView::meaningful)
                    .max(Comparator.comparingDouble(StockView::signalZ)).orElseThrow();
            headline = String.format(Locale.ROOT, "%d of %d stocks moved beyond their normal range since you last looked - %s stands out.",
                    meaningful, live.size(), short_(top.symbol()));
            tone = "alert";
        } else {
            headline = String.format(Locale.ROOT, "Quiet watchlist - everything is inside its normal range. Average move today %+.2f%%.", avg);
            tone = avg >= 0 ? "positive" : "negative";
        }

        List<String> bullets = new ArrayList<>();
        if (live.size() > 1) {
            bullets.add(String.format(Locale.ROOT, "Leader today: %s (%+.2f%%). Laggard: %s (%+.2f%%).",
                    short_(best.symbol()), best.dayChangePct(), short_(worst.symbol()), worst.dayChangePct()));
        }
        if (live.size() >= 3 && Math.abs(avg) >= 0.2) {
            live.stream()
                    .filter(v -> Math.signum(v.dayChangePct()) != Math.signum(avg) && Math.abs(v.dayChangePct()) >= 1.0)
                    .max(Comparator.comparingDouble(v -> Math.abs(v.dayChangePct())))
                    .ifPresent(v -> bullets.add(String.format(Locale.ROOT, "%s is bucking the trend (%+.2f%% while the list averages %+.2f%%).",
                            short_(v.symbol()), v.dayChangePct(), avg)));
        }
        long unusual = live.stream().filter(StockView::unusualToday).count();
        if (unusual > 0) bullets.add(unusual + (unusual == 1 ? " stock is" : " stocks are") + " having an unusually large day (2x+ their normal move).");
        long nearHigh = live.stream().filter(v -> v.rangePos52() >= 0.98).count();
        if (nearHigh > 0) bullets.add(nearHigh + (nearHigh == 1 ? " stock is" : " stocks are") + " at or within 2% of a 52-week high.");
        if (divergentPairs > 0) bullets.add(divergentPairs + (divergentPairs == 1 ? " pair has" : " pairs have") + " stopped moving together - see the Divergence Radar.");
        long stale = live.stream().filter(StockView::stale).count();
        if (stale > 0) bullets.add(stale + " price" + (stale == 1 ? " is" : "s are") + " delayed (upstream data temporarily unavailable).");

        return new DigestView(headline, tone, bullets, Math.round(avg * 100) / 100.0, up, down, flat, meaningful);
    }

    private static String short_(String symbol) {
        return symbol.replace(".NS", "").replace(".BO", "");
    }
}
