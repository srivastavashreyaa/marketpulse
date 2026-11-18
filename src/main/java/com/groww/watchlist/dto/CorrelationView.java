package com.groww.watchlist.dto;

import java.util.List;

public record CorrelationView(List<String> symbols, Double[][] matrix, List<Divergence> divergences) {

    public record Divergence(String symbolA, String symbolB, double longCorrelation, double recentCorrelation, String explanation) {}
}
