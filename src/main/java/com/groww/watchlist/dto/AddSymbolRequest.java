package com.groww.watchlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddSymbolRequest(@NotBlank(message = "Enter a ticker symbol") @Size(max = 20, message = "That ticker is too long") String symbol) {}
