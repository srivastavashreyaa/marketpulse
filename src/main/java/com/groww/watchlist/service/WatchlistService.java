package com.groww.watchlist.service;

import com.groww.watchlist.config.BadRequestException;
import com.groww.watchlist.dto.StockView;
import com.groww.watchlist.market.MarketDataService;
import com.groww.watchlist.market.StockData;
import com.groww.watchlist.model.LastSeenState;
import com.groww.watchlist.model.WatchlistItem;
import com.groww.watchlist.repository.LastSeenStateRepository;
import com.groww.watchlist.repository.WatchlistItemRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Write side: add/remove symbols and record what the user has seen. */
@Service
public class WatchlistService {

    private static final Pattern SYMBOL = Pattern.compile("^[A-Z0-9.^=\\-]{1,20}$");

    private final WatchlistItemRepository items;
    private final LastSeenStateRepository seenRepo;
    private final MarketDataService market;
    private final StockViewFactory views;
    private final int maxSymbols;

    public WatchlistService(WatchlistItemRepository items, LastSeenStateRepository seenRepo, MarketDataService market,
                            StockViewFactory views, @Value("${app.market.max-symbols-per-user:30}") int maxSymbols) {
        this.items = items;
        this.seenRepo = seenRepo;
        this.market = market;
        this.views = views;
        this.maxSymbols = maxSymbols;
    }

    public static String normalize(String raw) {
        String s = raw == null ? "" : raw.trim().toUpperCase();
        if (!SYMBOL.matcher(s).matches()) {
            throw new BadRequestException("Tickers can only contain letters, numbers and . - ^ =");
        }
        return s;
    }

    /** Validates against the market data provider BEFORE saving, so typos fail fast. */
    public StockView add(String userId, String rawSymbol) {
        String symbol = normalize(rawSymbol);
        StockData data = market.get(symbol); // throws UnknownSymbolException for bad tickers (no DB connection held)

        Optional<WatchlistItem> existing = items.findByUserIdAndSymbol(userId, symbol);
        if (existing.isEmpty()) {
            if (items.countByUserId(userId) >= maxSymbols) {
                throw new BadRequestException("Your watchlist is full (" + maxSymbols + " stocks). Remove one to add another.");
            }
            try {
                items.save(new WatchlistItem(userId, symbol, data.name(), Instant.now()));
                seenRepo.save(new LastSeenState(userId, symbol, data.price(), Instant.now()));
            } catch (DataIntegrityViolationException raced) {
                // two tabs added the same symbol at once; the row exists, which is what the caller wanted
            }
        }
        LastSeenState seen = seenRepo.findByUserIdAndSymbol(userId, symbol).orElse(null);
        return views.build(data, seen, Instant.now());
    }

    public List<StockView> addMany(String userId, List<String> rawSymbols) {
        List<String> symbols = rawSymbols.stream().map(WatchlistService::normalize).distinct().toList();
        market.getAll(symbols); // warm the cache in parallel; adds below are then instant
        List<StockView> added = new ArrayList<>();
        for (String s : symbols) {
            try {
                added.add(add(userId, s));
            } catch (RuntimeException ignored) {
                // skip symbols that fail; the rest still get added
            }
        }
        return added;
    }

    @Transactional
    public void remove(String userId, String rawSymbol) {
        String symbol = normalize(rawSymbol);
        items.deleteByUserIdAndSymbol(userId, symbol);
        seenRepo.deleteByUserIdAndSymbol(userId, symbol);
    }

    /**
     * Moves a guest's list into an account (used at sign-up). Symbols the account already has are kept as-is,
     * the account's size limit is respected, and the guest rows are removed afterwards.
     */
    @Transactional
    public void migrateGuest(String guestId, String ownerId) {
        if (guestId == null || guestId.equals(ownerId)) return;
        Map<String, WatchlistItem> have = new HashMap<>();
        for (WatchlistItem i : items.findByUserIdOrderByAddedAtAsc(ownerId)) have.put(i.getSymbol(), i);
        Map<String, LastSeenState> guestSeen = new HashMap<>();
        for (LastSeenState s : seenRepo.findByUserId(guestId)) guestSeen.put(s.getSymbol(), s);

        int room = maxSymbols - have.size();
        for (WatchlistItem g : items.findByUserIdOrderByAddedAtAsc(guestId)) {
            if (room <= 0 || have.containsKey(g.getSymbol())) continue;
            items.save(new WatchlistItem(ownerId, g.getSymbol(), g.getDisplayName(), g.getAddedAt()));
            LastSeenState s = guestSeen.get(g.getSymbol());
            if (s != null) seenRepo.save(new LastSeenState(ownerId, g.getSymbol(), s.getLastSeenPrice(), s.getLastSeenAt()));
            room--;
        }
        items.deleteByUserId(guestId);
        seenRepo.deleteByUserId(guestId);
    }

    /** "I've looked at everything" - reset the baseline to the latest known prices. */
    @Transactional
    public void markAllSeen(String userId) {
        Map<String, LastSeenState> seen = new HashMap<>();
        for (LastSeenState s : seenRepo.findByUserId(userId)) seen.put(s.getSymbol(), s);
        Instant now = Instant.now();
        List<LastSeenState> toSave = new ArrayList<>();
        for (WatchlistItem item : items.findByUserIdOrderByAddedAtAsc(userId)) {
            Optional<StockData> latest = market.peek(item.getSymbol());
            if (latest.isEmpty()) continue;
            LastSeenState state = seen.get(item.getSymbol());
            if (state == null) {
                state = new LastSeenState(userId, item.getSymbol(), latest.get().price(), now);
            } else {
                state.update(latest.get().price(), now);
            }
            toSave.add(state);
        }
        seenRepo.saveAll(toSave);
    }
}
