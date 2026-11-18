package com.groww.watchlist.analytics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class StatsTest {

    @Test
    void returnsArePercentages() {
        assertThat(Stats.returnsPct(new double[]{100, 110, 99})).containsExactly(10.0, -10.0);
    }

    @Test
    void stdDevIsSampleStdDev() {
        assertThat(Stats.stdDev(new double[]{2, 4, 4, 4, 5, 5, 7, 9})).isCloseTo(2.138, within(0.001));
        assertThat(Stats.stdDev(new double[]{5})).isZero();
    }

    @Test
    void pearsonHandlesPerfectInverseAndConstantSeries() {
        double[] x = {1, 2, 3, 4, 5};
        assertThat(Stats.pearson(x, new double[]{2, 4, 6, 8, 10})).isCloseTo(1.0, within(1e-9));
        assertThat(Stats.pearson(x, new double[]{5, 4, 3, 2, 1})).isCloseTo(-1.0, within(1e-9));
        assertThat(Stats.pearson(x, new double[]{3, 3, 3, 3, 3})).isZero();
    }

    @Test
    void rsiIsExtremeOnMonotonicSeries() {
        double[] up = new double[30], down = new double[30];
        for (int i = 0; i < 30; i++) { up[i] = 100 + i; down[i] = 200 - i; }
        assertThat(Stats.rsi(up, 14)).isEqualTo(100.0);
        assertThat(Stats.rsi(down, 14)).isZero();
        assertThat(Stats.rsi(new double[]{1, 2, 3}, 14)).isEqualTo(50.0);
    }

    @Test
    void smaIsNullUntilWindowFills() {
        Double[] sma = Stats.sma(new double[]{1, 2, 3, 4, 5}, 3);
        assertThat(sma[1]).isNull();
        assertThat(sma[2]).isEqualTo(2.0);
        assertThat(sma[4]).isEqualTo(4.0);
    }
}
