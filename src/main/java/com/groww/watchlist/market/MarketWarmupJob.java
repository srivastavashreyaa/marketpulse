package com.groww.watchlist.market;

import com.groww.watchlist.model.WatchlistItem;
import com.groww.watchlist.repository.WatchlistItemRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Keeps the shared cache warm for every symbol anyone is watching (plus the
 * market indices), so the dashboard request is almost always served from memory.
 */
@Component
public class MarketWarmupJob {

    private final WatchlistItemRepository items;
    private final MarketDataService market;

    public MarketWarmupJob(WatchlistItemRepository items, MarketDataService market) {
        this.items = items;
        this.market = market;
    }

    @Scheduled(initialDelay = 2_000, fixedDelay = 30_000)
    public void warm() {
        Set<String> symbols = new LinkedHashSet<>(MarketIndices.NAMES.keySet());
        for (WatchlistItem item : items.findAll()) symbols.add(item.getSymbol());
        market.getAll(symbols);
    }
}
