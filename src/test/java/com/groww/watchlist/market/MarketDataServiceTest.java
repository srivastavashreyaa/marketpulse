package com.groww.watchlist.market;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class MarketDataServiceTest {

    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final SymbolCatalog catalog = new SymbolCatalog();

    private MarketDataService service(boolean demoFallback) {
        return new MarketDataService(new YahooProvider(), new DemoProvider(catalog), catalog, pool, false, demoFallback, 45);
    }

    @AfterEach
    void shutdown() { pool.shutdownNow(); }

    @Test
    void fallsBackToLabelledDemoDataAndCaches() {
        MarketDataService s = service(true);
        StockData first = s.get(" tcs.ns ");
        assertThat(first.symbol()).isEqualTo("TCS.NS");
        assertThat(first.demo()).isTrue();
        assertThat(s.get("TCS.NS")).isSameAs(first);
        assertThat(s.dataMode()).isEqualTo("demo");
    }

    @Test
    void batchFetchReturnsEverySymbol() {
        Map<String, StockData> all = service(true).getAll(List.of("AAPL", "MSFT", "INFY.NS", "^NSEI"));
        assertThat(all).containsOnlyKeys("AAPL", "MSFT", "INFY.NS", "^NSEI");
    }

    @Test
    void demoSeriesIsDeterministicForTheSameDay() {
        DemoProvider demo = new DemoProvider(catalog);
        assertThat(demo.generate("AAPL").closes()[10]).isEqualTo(demo.generate("AAPL").closes()[10]);
    }

    @Test
    void searchPrefersSymbolPrefixThenName() {
        assertThat(catalog.search("tcs", 8)).extracting("symbol").first().isEqualTo("TCS.NS");
        assertThat(catalog.search("apple", 8)).extracting("symbol").contains("AAPL");
    }
}
