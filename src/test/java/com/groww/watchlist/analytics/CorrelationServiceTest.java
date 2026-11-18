package com.groww.watchlist.analytics;

import com.groww.watchlist.market.StockData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationServiceTest {

    private final CorrelationService service = new CorrelationService();

    private StockData fromReturns(String sym, double[] returns, long firstDay) {
        double[] closes = new double[returns.length + 1];
        long[] days = new long[closes.length];
        closes[0] = 100;
        days[0] = firstDay;
        for (int i = 0; i < returns.length; i++) { closes[i + 1] = closes[i] * (1 + returns[i] / 100); days[i + 1] = firstDay + i + 1; }
        return TestData.stock(sym, closes, days, closes[closes.length - 1]);
    }

    @Test
    void identicalMoversAreFullyCorrelatedWithUnitDiagonal() {
        Random r = new Random(7);
        double[] ret = new double[120];
        for (int i = 0; i < ret.length; i++) ret[i] = r.nextGaussian();
        var res = service.analyse(List.of(fromReturns("A", ret, 20_000), fromReturns("B", ret, 20_000)));
        assertThat(res.matrix()[0][0]).isEqualTo(1.0);
        assertThat(res.matrix()[0][1]).isEqualTo(1.0);
        assertThat(res.matrix()[1][0]).isEqualTo(1.0);
        assertThat(res.divergences()).isEmpty();
    }

    @Test
    void relationshipThatBreaksDownRecentlyIsFlagged() {
        Random r = new Random(11);
        double[] a = new double[100], b = new double[100];
        for (int i = 0; i < 100; i++) {
            a[i] = r.nextGaussian();
            b[i] = i < 90 ? a[i] + 0.1 * r.nextGaussian() : -a[i] + 0.1 * r.nextGaussian(); // last 10 days flip
        }
        var res = service.analyse(List.of(fromReturns("A", a, 20_000), fromReturns("B", b, 20_000)));
        assertThat(res.divergences()).hasSize(1);
        assertThat(res.divergences().get(0).recentCorrelation()).isLessThan(0);
    }

    @Test
    void alignmentIsByDateNotByPosition() {
        Random r = new Random(3);
        double[] ret = new double[120];
        for (int i = 0; i < ret.length; i++) ret[i] = r.nextGaussian();
        // B starts 5 days later but has the same returns on the same calendar days
        double[] shifted = java.util.Arrays.copyOfRange(ret, 5, ret.length);
        var res = service.analyse(List.of(fromReturns("A", ret, 20_000), fromReturns("B", shifted, 20_005)));
        assertThat(res.matrix()[0][1]).isEqualTo(1.0);
    }

    @Test
    void tooLittleOverlapGivesNoCorrelation() {
        double[] ret = new double[10];
        var res = service.analyse(List.of(fromReturns("A", ret, 20_000), fromReturns("B", ret, 20_000)));
        assertThat(res.matrix()[0][1]).isNull();
    }
}
