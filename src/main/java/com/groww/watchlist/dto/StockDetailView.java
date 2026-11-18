package com.groww.watchlist.dto;

public record StockDetailView(StockView stock, String[] dates, double[] closes, Double[] sma20, Double[] sma50,
                              BacktestView backtest, boolean inWatchlist) {}
