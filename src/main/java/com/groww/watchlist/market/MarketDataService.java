package com.groww.watchlist.market;

import com.groww.watchlist.config.DataUnavailableException;
import com.groww.watchlist.config.UnknownSymbolException;
import com.groww.watchlist.dto.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * The single entry point for market data. Responsibilities:
 * <ul>
 *   <li><b>Shared cache</b> - one entry per symbol for all users (45s TTL).</li>
 *   <li><b>Request coalescing</b> - N simultaneous requests for a symbol cause ONE upstream call.</li>
 *   <li><b>Parallel batch fetch</b> - a 20-stock dashboard costs about one round trip, not twenty.</li>
 *   <li><b>Circuit breaker</b> - after an upstream failure we stop trying for 45s, so users never wait on timeouts.</li>
 *   <li><b>Graceful degradation</b> - last-known-live data (flagged stale) first, then labelled demo data.</li>
 * </ul>
 */
@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);
    private static final long BREAKER_MILLIS = 45_000;
    private static final Duration DEMO_TTL = Duration.ofSeconds(15);

    private final YahooProvider yahoo;
    private final DemoProvider demo;
    private final SymbolCatalog catalog;
    private final ExecutorService executor;
    private final boolean yahooEnabled;
    private final boolean demoFallback;
    private final Duration ttl;

    private final Map<String, StockData> cache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<StockData>> inflight = new ConcurrentHashMap<>();
    private volatile long yahooBlockedUntil = 0;
    private volatile Instant lastLiveSuccess = null;

    public MarketDataService(YahooProvider yahoo, DemoProvider demo, SymbolCatalog catalog,
                             @Qualifier("marketExecutor") ExecutorService executor,
                             @Value("${app.market.yahoo-enabled:true}") boolean yahooEnabled,
                             @Value("${app.market.demo-fallback:true}") boolean demoFallback,
                             @Value("${app.market.cache-ttl-seconds:45}") long ttlSeconds) {
        this.yahoo = yahoo;
        this.demo = demo;
        this.catalog = catalog;
        this.executor = executor;
        this.yahooEnabled = yahooEnabled;
        this.demoFallback = demoFallback;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public StockData get(String rawSymbol) {
        String symbol = rawSymbol.trim().toUpperCase();
        StockData cached = cache.get(symbol);
        if (cached != null && isFresh(cached)) return cached;

        CompletableFuture<StockData> mine = new CompletableFuture<>();
        CompletableFuture<StockData> existing = inflight.putIfAbsent(symbol, mine);
        if (existing != null) return await(existing);
        try {
            StockData loaded = load(symbol, cached);
            mine.complete(loaded);
            return loaded;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inflight.remove(symbol);
        }
    }

    /** Whatever is in cache right now, fresh or not, without touching the network. */
    public Optional<StockData> peek(String symbol) {
        return Optional.ofNullable(cache.get(symbol.trim().toUpperCase()));
    }

    /** Fetches many symbols in parallel. Symbols that fail are simply absent from the result. */
    public Map<String, StockData> getAll(java.util.Collection<String> symbols) {
        Map<String, CompletableFuture<StockData>> futures = new LinkedHashMap<>();
        for (String s : symbols) {
            futures.computeIfAbsent(s.trim().toUpperCase(), k -> CompletableFuture.supplyAsync(() -> get(k), executor));
        }
        Map<String, StockData> out = new LinkedHashMap<>();
        futures.forEach((symbol, f) -> {
            try {
                out.put(symbol, f.join());
            } catch (CompletionException e) {
                log.debug("No data for {}: {}", symbol, e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
            }
        });
        return out;
    }

    public List<SearchResult> search(String query) {
        List<SearchResult> out = new ArrayList<>(catalog.search(query, 8));
        if (out.size() < 5 && yahooEnabled && System.currentTimeMillis() >= yahooBlockedUntil) {
            for (SearchResult r : yahoo.search(query)) {
                if (out.stream().noneMatch(x -> x.symbol().equalsIgnoreCase(r.symbol()))) out.add(r);
            }
        }
        return out.size() > 8 ? out.subList(0, 8) : out;
    }

    /** "live" when Yahoo answered recently, otherwise "demo" (fallback on) or "offline". */
    public String dataMode() {
        Instant ok = lastLiveSuccess;
        if (ok != null && Duration.between(ok, Instant.now()).toMinutes() < 3) return "live";
        return demoFallback ? "demo" : "offline";
    }

    // ---------------------------------------------------------------- internals

    private StockData load(String symbol, StockData previous) {
        if (yahooEnabled && System.currentTimeMillis() >= yahooBlockedUntil) {
            FetchResult r = yahoo.fetch(symbol);
            switch (r.status()) {
                case OK -> {
                    lastLiveSuccess = Instant.now();
                    cache.put(symbol, r.data());
                    return r.data();
                }
                case NOT_FOUND -> throw new UnknownSymbolException(symbol);
                case UNAVAILABLE -> {
                    yahooBlockedUntil = System.currentTimeMillis() + BREAKER_MILLIS;
                    log.warn("Yahoo unavailable - circuit open for {}s (serving stale/demo data)", BREAKER_MILLIS / 1000);
                }
            }
        }
        if (previous != null && !previous.demo()) {
            return previous.asStale(); // last known real price, flagged
        }
        if (demoFallback) {
            StockData d = demo.generate(symbol);
            cache.put(symbol, d);
            return d;
        }
        throw new DataUnavailableException(symbol);
    }

    private boolean isFresh(StockData d) {
        if (d.stale()) return false;
        Duration limit = d.demo() ? DEMO_TTL : ttl;
        return Duration.between(d.fetchedAt(), Instant.now()).compareTo(limit) < 0;
    }

    private static StockData await(CompletableFuture<StockData> f) {
        try {
            return f.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw e;
        }
    }
}
