package com.groww.watchlist.dto;

import java.util.List;

public record DigestView(String headline, String tone, List<String> bullets,
                         double avgDayChangePct, int advancers, int decliners, int unchanged, int meaningfulCount) {}
