package com.groww.watchlist.controller;

import com.groww.watchlist.auth.Owners;
import com.groww.watchlist.dto.*;
import com.groww.watchlist.market.MarketDataService;
import com.groww.watchlist.service.DashboardService;
import com.groww.watchlist.service.WatchlistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API. A request belongs to the logged-in account (session cookie) or, failing that, to the anonymous
 * guest id the browser sends as X-User-Id. See {@link Owners}.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final DashboardService dashboard;
    private final WatchlistService watchlist;
    private final MarketDataService market;
    private final Owners owners;

    public ApiController(DashboardService dashboard, WatchlistService watchlist, MarketDataService market, Owners owners) {
        this.dashboard = dashboard;
        this.watchlist = watchlist;
        this.market = market;
        this.owners = owners;
    }

    @GetMapping("/dashboard")
    public DashboardView dashboard(@RequestHeader(value = "X-User-Id", required = false) String userId, @CookieValue(value = "sw_session", required = false) String session) {
        return dashboard.dashboard(owners.resolve(session, userId));
    }

    @GetMapping("/stocks/{symbol}")
    public StockDetailView detail(@RequestHeader(value = "X-User-Id", required = false) String userId, @CookieValue(value = "sw_session", required = false) String session, @PathVariable String symbol) {
        return dashboard.detail(owners.resolve(session, userId), symbol);
    }

    @GetMapping("/search")
    public List<SearchResult> search(@RequestParam("q") String q) {
        if (q == null || q.isBlank() || q.length() > 40) return List.of();
        return market.search(q);
    }

    @PostMapping("/watchlist")
    public ResponseEntity<StockView> add(@RequestHeader(value = "X-User-Id", required = false) String userId, @CookieValue(value = "sw_session", required = false) String session, @Valid @RequestBody AddSymbolRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(watchlist.add(owners.resolve(session, userId), req.symbol()));
    }

    @PostMapping("/watchlist/bulk")
    public ResponseEntity<List<StockView>> addMany(@RequestHeader(value = "X-User-Id", required = false) String userId, @CookieValue(value = "sw_session", required = false) String session, @Valid @RequestBody BulkAddRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(watchlist.addMany(owners.resolve(session, userId), req.symbols()));
    }

    @DeleteMapping("/watchlist/{symbol}")
    public ResponseEntity<Void> remove(@RequestHeader(value = "X-User-Id", required = false) String userId, @CookieValue(value = "sw_session", required = false) String session, @PathVariable String symbol) {
        watchlist.remove(owners.resolve(session, userId), symbol);
        return ResponseEntity.noContent().build();
    }

    /** Accepts the id as a query param too, because navigator.sendBeacon() cannot set headers. */
    @PostMapping("/watchlist/seen")
    public ResponseEntity<Void> seen(@RequestHeader(value = "X-User-Id", required = false) String header,
                                     @CookieValue(value = "sw_session", required = false) String session,
                                     @RequestParam(value = "uid", required = false) String param) {
        watchlist.markAllSeen(owners.resolve(session, header != null ? header : param));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/status")
    public StatusView status() {
        String mode = market.dataMode();
        String msg = switch (mode) {
            case "live" -> "Live market data";
            case "demo" -> "Live feed unreachable - showing clearly-labelled demo data";
            default -> "Market data offline";
        };
        return new StatusView(mode, msg);
    }
}
