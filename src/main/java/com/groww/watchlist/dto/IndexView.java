package com.groww.watchlist.dto;

public record IndexView(String symbol, String name, double price, double changePct, double[] spark, String source) {}
