package com.groww.watchlist.market;

import java.util.LinkedHashMap;
import java.util.Map;

/** Benchmark symbols shown in the market strip (symbol -> display name). */
public final class MarketIndices {

    public static final Map<String, String> NAMES = new LinkedHashMap<>();
    static {
        NAMES.put("^NSEI", "NIFTY 50");
        NAMES.put("^BSESN", "SENSEX");
        NAMES.put("^GSPC", "S&P 500");
        NAMES.put("^IXIC", "NASDAQ");
        NAMES.put("BTC-USD", "Bitcoin");
    }

    private MarketIndices() {}
}
