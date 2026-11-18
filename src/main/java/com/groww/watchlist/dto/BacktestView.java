package com.groww.watchlist.dto;

import java.util.List;

public record BacktestView(String symbol, int daysTested, int timesFlagged, double flagRatePct,
                           double avgFlaggedMovePct, double maxFlaggedMovePct,
                           Double continuationRatePct, double baselineContinuationPct, Double avgNextDayPct,
                           double currentThresholdPct, String verdict, String note, List<ReturnPoint> series) {

    public record ReturnPoint(String date, double retPct, double thresholdPct, boolean flagged) {}
}
