package com.groww.watchlist.market;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Deterministic synthetic market used ONLY as a clearly-labelled fallback
 * (the UI shows a "DEMO DATA" badge) when the live provider is unreachable,
 * e.g. when Yahoo rate-limits a cloud host's IP, and for offline development
 * and tests.
 *
 * The generator shares a market factor and a sector factor across symbols so
 * that correlation analysis has something realistic to find, and adds fat-tailed
 * idiosyncratic shocks so the alert rule actually fires sometimes.
 */
@Component
public class DemoProvider {

    private static final int BARS = 252;
    private final SymbolCatalog catalog;

    public DemoProvider(SymbolCatalog catalog) {
        this.catalog = catalog;
    }

    public StockData generate(String symbol) {
        SymbolCatalog.Entry entry = catalog.find(symbol);
        int h = symbol.hashCode();
        boolean index = symbol.startsWith("^") || (entry != null && "INDEX".equals(entry.type()));
        double base = entry != null ? entry.basePrice() : 50 + Math.abs(h % 4000) / 10.0;
        int sector = entry != null ? entry.sector() : Math.floorMod(h, 5);
        double vol = index ? 0.0045 : 0.007 + Math.floorMod(h / 7, 10) * 0.0011;
        double beta = index ? 1.0 : 0.5 + Math.floorMod(h / 13, 10) * 0.08;

        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = LocalDate.now(ZoneOffset.UTC); dates.size() < BARS; d = d.minusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) dates.add(d);
        }
        Collections.reverse(dates);

        long[] days = new long[BARS];
        double[] closes = new double[BARS];
        double p = 100;
        for (int i = 0; i < BARS; i++) {
            long dn = dates.get(i).toEpochDay();
            days[i] = dn;
            double market = gauss(new SplittableRandom(dn * 7919L + 17));
            double sec = sector < 0 ? 0 : gauss(new SplittableRandom(dn * 104729L + sector * 31L + 5));
            SplittableRandom r = new SplittableRandom(dn * 31L + h);
            double idio = gauss(r);
            if (r.nextDouble() < 0.04) idio *= 3.2; // fat tail: occasional genuine shock
            double ret = 0.0005 + beta * 0.006 * market + 0.010 * sec + vol * 0.8 * idio;
            p *= 1 + ret;
            closes[i] = p;
        }
        double scale = base / closes[BARS - 1];
        for (int i = 0; i < BARS; i++) closes[i] *= scale;

        // Gentle intraday wiggle so the live dashboard visibly ticks (weekdays only).
        boolean tradingToday = dates.get(BARS - 1).equals(LocalDate.now(ZoneOffset.UTC));
        double price = closes[BARS - 1];
        if (tradingToday) {
            long bucket = Instant.now().getEpochSecond() / 15;
            price *= 1 + gauss(new SplittableRandom(bucket * 977L + h)) * 0.0007;
            closes[BARS - 1] = price;
        }
        double previousClose = tradingToday ? closes[BARS - 2] : closes[BARS - 1];

        double min = Double.MAX_VALUE, max = 0;
        for (double c : closes) { min = Math.min(min, c); max = Math.max(max, c); }

        String name = entry != null ? entry.name() : symbol;
        String exchange = entry != null ? entry.exchange() : "DEMO";
        String currency = symbol.endsWith(".NS") || symbol.endsWith(".BO") || symbol.equals("^NSEI") || symbol.equals("^BSESN")
                ? "INR" : "USD";
        double hi = Math.max(price, previousClose) * (1 + 0.002 + vol / 3);
        double lo = Math.min(price, previousClose) * (1 - 0.002 - vol / 3);
        long volume = 1_000_000L + Math.floorMod(h, 60) * 1_000_000L;
        return new StockData(symbol, name, currency, exchange, price, previousClose, hi, lo,
                max, min, volume, days, closes, StockData.DEMO, false, Instant.now());
    }

    private static double gauss(SplittableRandom r) {
        double u1 = 1 - r.nextDouble();
        double u2 = r.nextDouble();
        return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
    }
}
