package com.groww.watchlist.service;

import com.groww.watchlist.analytics.BacktestService;
import com.groww.watchlist.analytics.CorrelationService;
import com.groww.watchlist.analytics.DigestService;
import com.groww.watchlist.analytics.Stats;
import com.groww.watchlist.dto.*;
import com.groww.watchlist.market.MarketDataService;
import com.groww.watchlist.market.MarketIndices;
import com.groww.watchlist.market.StockData;
import com.groww.watchlist.model.LastSeenState;
import com.groww.watchlist.model.WatchlistItem;
import com.groww.watchlist.repository.LastSeenStateRepository;
import com.groww.watchlist.repository.WatchlistItemRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Read side: assembles the whole dashboard (and the stock detail drawer) from cached market data. */
@Service
public class DashboardService {

    private final WatchlistItemRepository items;
    private final LastSeenStateRepository seenRepo;
    private final MarketDataService market;
    private final StockViewFactory views;
    private final CorrelationService correlation;
    private final DigestService digest;
    private final BacktestService backtest;

    public DashboardService(WatchlistItemRepository items, LastSeenStateRepository seenRepo, MarketDataService market,
                            StockViewFactory views, CorrelationService correlation, DigestService digest,
                            BacktestService backtest) {
        this.items = items;
        this.seenRepo = seenRepo;
        this.market = market;
        this.views = views;
        this.correlation = correlation;
        this.digest = digest;
        this.backtest = backtest;
    }

    public DashboardView dashboard(String userId) {
        List<WatchlistItem> watched = items.findByUserIdOrderByAddedAtAsc(userId);
        Map<String, LastSeenState> seen = seenByUser(userId);

        List<String> all = new ArrayList<>();
        watched.forEach(i -> all.add(i.getSymbol()));
        all.addAll(MarketIndices.NAMES.keySet());
        Map<String, StockData> data = market.getAll(all); // parallel + cached
        Instant now = Instant.now();

        List<StockView> stockViews = new ArrayList<>();
        List<StockData> usable = new ArrayList<>();
        for (WatchlistItem item : watched) {
            StockData d = data.get(item.getSymbol());
            if (d == null) {
                stockViews.add(StockView.unavailable(item.getSymbol(), item.getDisplayName()));
            } else {
                stockViews.add(views.build(d, seen.get(item.getSymbol()), now));
                usable.add(d);
            }
        }

        List<IndexView> indices = new ArrayList<>();
        MarketIndices.NAMES.forEach((symbol, name) -> {
            StockData d = data.get(symbol);
            if (d == null) return;
            double change = d.previousClose() > 0 ? (d.price() - d.previousClose()) / d.previousClose() * 100 : 0;
            double[] closes = d.closes();
            double[] spark = Arrays.copyOfRange(closes, Math.max(0, closes.length - 30), closes.length);
            indices.add(new IndexView(symbol, name, d.price(), Stats.round(change, 2), spark, d.source()));
        });

        CorrelationView corr = correlation.analyse(usable);
        List<String> topMovers = stockViews.stream().filter(StockView::available)
                .sorted(Comparator.comparingDouble((StockView v) -> Math.abs(v.dayChangePct())).reversed())
                .limit(3).map(StockView::symbol).collect(Collectors.toList());

        return new DashboardView(stockViews, indices, digest.build(stockViews, corr.divergences().size()),
                corr, topMovers, market.dataMode(), now);
    }

    public StockDetailView detail(String userId, String rawSymbol) {
        String symbol = WatchlistService.normalize(rawSymbol);
        StockData d = market.get(symbol);
        LastSeenState seen = seenRepo.findByUserIdAndSymbol(userId, symbol).orElse(null);
        boolean tracked = items.findByUserIdAndSymbol(userId, symbol).isPresent();

        double[] closes = d.closes();
        String[] dates = new String[closes.length];
        double[] rounded = new double[closes.length];
        for (int i = 0; i < closes.length; i++) {
            dates[i] = LocalDate.ofEpochDay(d.days()[i]).toString();
            rounded[i] = Stats.round(closes[i], 2);
        }
        return new StockDetailView(views.build(d, seen, Instant.now()), dates, rounded,
                round(Stats.sma(closes, 20)), round(Stats.sma(closes, 50)), backtest.run(d), tracked);
    }

    private Map<String, LastSeenState> seenByUser(String userId) {
        Map<String, LastSeenState> map = new HashMap<>();
        for (LastSeenState s : seenRepo.findByUserId(userId)) map.put(s.getSymbol(), s);
        return map;
    }

    private static Double[] round(Double[] a) {
        Double[] out = new Double[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i] == null ? null : Stats.round(a[i], 2);
        return out;
    }
}
