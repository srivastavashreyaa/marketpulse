package com.groww.watchlist.analytics;

import com.groww.watchlist.dto.BacktestView;
import com.groww.watchlist.market.DemoProvider;
import com.groww.watchlist.market.StockData;
import com.groww.watchlist.market.SymbolCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BacktestServiceTest {

    private final BacktestService service = new BacktestService();
    private final DemoProvider demo = new DemoProvider(new SymbolCatalog());

    @Test
    void producesConsistentCounts() {
        BacktestView v = service.run(demo.generate("TCS.NS"));
        assertThat(v.daysTested()).isGreaterThan(100);
        assertThat(v.timesFlagged()).isBetween(0, v.daysTested());
        assertThat(v.series()).hasSizeLessThanOrEqualTo(120);
        assertThat(v.series().stream().filter(BacktestView.ReturnPoint::flagged).count())
                .isLessThanOrEqualTo(v.timesFlagged());
        assertThat(v.baselineContinuationPct()).isBetween(0.0, 100.0);
    }

    @Test
    void thresholdUsesOnlyPriorDataSoALoneSpikeIsAlwaysFlagged() {
        // flat-ish history then one 10% shock: trailing sigma is tiny, so the spike must be flagged
        double[] closes = new double[80];
        long[] days = new long[80];
        for (int i = 0; i < 80; i++) { closes[i] = 100 + (i % 2) * 0.1; days[i] = 20_000 + i; }
        closes[60] = closes[59] * 1.10;
        for (int i = 61; i < 80; i++) closes[i] = closes[60] + (i % 2) * 0.1;
        StockData d = TestData.stock("X", closes, days, closes[79]);
        BacktestView v = service.run(d);
        assertThat(v.series().stream().anyMatch(p -> p.flagged() && p.retPct() > 9)).isTrue();
    }

    @Test
    void shortHistoryIsReportedHonestly() {
        double[] closes = new double[20];
        long[] days = new long[20];
        for (int i = 0; i < 20; i++) { closes[i] = 100 + i; days[i] = 20_000 + i; }
        BacktestView v = service.run(TestData.stock("X", closes, days, 119));
        assertThat(v.verdict()).isEqualTo("insufficient");
        assertThat(v.daysTested()).isZero();
    }
}
