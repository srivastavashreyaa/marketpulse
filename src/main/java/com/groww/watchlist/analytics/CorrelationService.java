package com.groww.watchlist.analytics;

import com.groww.watchlist.dto.CorrelationView;
import com.groww.watchlist.dto.CorrelationView.Divergence;
import com.groww.watchlist.market.StockData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Pairwise correlation across the watchlist, plus the "Divergence Radar":
 * pairs that usually move together but have recently stopped.
 *
 * <p>Series are aligned by exchange-calendar DATE (not by array position), so a
 * US holiday or an NSE holiday never shifts one stock against another.
 */
@org.springframework.stereotype.Service
public class CorrelationService {

    static final int LONG_WINDOW = 90;
    static final int RECENT_WINDOW = 10;
    static final int MIN_COMMON_DAYS = 30;
    static final double MIN_LONG_CORR = 0.5;
    static final double MIN_BREAKDOWN_GAP = 0.6;
    static final int MAX_SYMBOLS = 30;

    public CorrelationView analyse(List<StockData> stocks) {
        List<StockData> use = stocks.size() > MAX_SYMBOLS ? stocks.subList(0, MAX_SYMBOLS) : stocks;
        int n = use.size();
        List<Map<Long, Double>> byDay = new ArrayList<>();
        for (StockData s : use) byDay.add(returnsByDay(s));

        Double[][] matrix = new Double[n][n];
        List<Divergence> divergences = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            matrix[i][i] = 1.0;
            for (int j = i + 1; j < n; j++) {
                double[][] common = commonReturns(byDay.get(i), byDay.get(j));
                if (common[0].length < MIN_COMMON_DAYS) {
                    matrix[i][j] = matrix[j][i] = null;
                    continue;
                }
                double longCorr = Stats.pearson(common[0], common[1]);
                matrix[i][j] = matrix[j][i] = Stats.round(longCorr, 2);

                double recent = Stats.pearson(Stats.tail(common[0], RECENT_WINDOW), Stats.tail(common[1], RECENT_WINDOW));
                if (Math.abs(longCorr) >= MIN_LONG_CORR && Math.abs(longCorr - recent) >= MIN_BREAKDOWN_GAP) {
                    String a = use.get(i).symbol(), b = use.get(j).symbol();
                    divergences.add(new Divergence(a, b, Stats.round(longCorr, 2), Stats.round(recent, 2),
                            String.format(Locale.ROOT,
                                    "%s and %s usually move together (correlation %.2f over %d days) but have decoupled over the last %d days (%.2f). Worth checking whether something stock-specific is happening.",
                                    a, b, longCorr, common[0].length, RECENT_WINDOW, recent)));
                }
            }
        }
        divergences.sort((x, y) -> Double.compare(Math.abs(y.longCorrelation() - y.recentCorrelation()),
                Math.abs(x.longCorrelation() - x.recentCorrelation())));
        return new CorrelationView(use.stream().map(StockData::symbol).toList(), matrix, divergences);
    }

    private Map<Long, Double> returnsByDay(StockData s) {
        double[] r = Stats.returnsPct(s.closes());
        Map<Long, Double> m = new HashMap<>();
        for (int i = 0; i < r.length; i++) m.put(s.days()[i + 1], r[i]);
        return m;
    }

    /** Returns [xs, ys] over the most recent LONG_WINDOW dates both symbols traded. */
    private double[][] commonReturns(Map<Long, Double> a, Map<Long, Double> b) {
        TreeSet<Long> days = new TreeSet<>(a.keySet());
        days.retainAll(b.keySet());
        List<Long> list = new ArrayList<>(days);
        if (list.size() > LONG_WINDOW) list = list.subList(list.size() - LONG_WINDOW, list.size());
        double[] xs = new double[list.size()], ys = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            xs[i] = a.get(list.get(i));
            ys[i] = b.get(list.get(i));
        }
        return new double[][]{xs, ys};
    }
}
