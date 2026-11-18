package com.groww.watchlist.model;

import jakarta.persistence.*;

import java.time.Instant;

/** One stock a user tracks. userId is a client-generated UUID (no accounts needed). */
@Entity
@Table(name = "watchlist_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "symbol"}),
        indexes = @Index(name = "idx_watchlist_user", columnList = "user_id"))
public class WatchlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 24)
    private String symbol;

    private String displayName;

    @Column(nullable = false)
    private Instant addedAt;

    protected WatchlistItem() {}

    public WatchlistItem(String userId, String symbol, String displayName, Instant addedAt) {
        this.userId = userId;
        this.symbol = symbol;
        this.displayName = displayName;
        this.addedAt = addedAt;
    }

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public String getSymbol() { return symbol; }
    public String getDisplayName() { return displayName; }
    public Instant getAddedAt() { return addedAt; }
}
