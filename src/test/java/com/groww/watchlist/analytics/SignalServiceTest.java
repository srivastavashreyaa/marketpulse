package com.groww.watchlist.analytics;

import com.groww.watchlist.market.StockData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SignalServiceTest {

    private final SignalService service = new SignalService();
    private final Instant now = Instant.now();

    @Test
    void sigmaComesFromRealHistory() {
        StockData d = TestData.alternating("X", 1.0, 80, 100);
        assertThat(service.dailySigma(d.closes())).isCloseTo(1.0, within(0.1));
    }

    @Test
    void largeMoveSinceLastLookIsMeaningfulButSmallOneIsNot() {
        StockData big = TestData.alternating("X", 1.0, 80, 103);
        StockData small = TestData.alternating("X", 1.0, 80, 100.5);
        assertThat(service.evaluate(big, 100.0, now, now).meaningful()).isTrue();
        assertThat(service.evaluate(small, 100.0, now, now).meaningful()).isFalse();
    }

    @Test
    void thresholdGrowsWithTimeAway() {
        double sigma = 1.0;
        double sameDay = service.thresholdPct(sigma, service.tradingDaysAway(now, now));
        double weekAway = service.thresholdPct(sigma, service.tradingDaysAway(now.minus(7, ChronoUnit.DAYS), now));
        assertThat(sameDay).isCloseTo(1.5, within(1e-9));
        assertThat(weekAway).isCloseTo(1.5 * Math.sqrt(5), within(1e-6)); // 7 calendar days = 5 trading days
    }

    @Test
    void sameMoveIsLessSurprisingAfterALongAbsence() {
        StockData d = TestData.alternating("X", 1.0, 80, 103);
        assertThat(service.evaluate(d, 100.0, now.minus(7, ChronoUnit.DAYS), now).meaningful()).isFalse();
    }

    @Test
    void firstTimeTrackingHasNoChangeAndNoSignal() {
        StockData d = TestData.alternating("X", 1.0, 80, 103);
        SignalService.Signal s = service.evaluate(d, null, null, now);
        assertThat(s.changeSinceSeenPct()).isNull();
        assertThat(s.meaningful()).isFalse();
    }

    @Test
    void newFiftyTwoWeekHighIsFlagged() {
        double[] closes = new double[80];
        long[] days = new long[80];
        for (int i = 0; i < 80; i++) { closes[i] = 100 + (i % 2); days[i] = 20_000 + i; }
        closes[79] = 105;
        StockData d = new StockData("X", "X", "USD", "T", 105, closes[78], 105, 100, 105, 99, 0, days, closes, StockData.LIVE, false, now);
        assertThat(service.evaluate(d, 101.0, now, now).crossedHigh()).isTrue();
    }
}
