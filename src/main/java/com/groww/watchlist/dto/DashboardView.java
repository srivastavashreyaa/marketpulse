package com.groww.watchlist.dto;

import java.time.Instant;
import java.util.List;

/** Everything the dashboard needs in ONE response (a single round trip). */
public record DashboardView(List<StockView> items, List<IndexView> indices, DigestView digest,
                            CorrelationView correlation, List<String> topMovers,
                            String dataMode, Instant serverTime) {}
