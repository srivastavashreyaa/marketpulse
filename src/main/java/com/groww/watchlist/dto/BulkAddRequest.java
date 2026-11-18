package com.groww.watchlist.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkAddRequest(@NotEmpty(message = "No symbols given") @Size(max = 15, message = "Too many symbols at once") List<String> symbols) {}
