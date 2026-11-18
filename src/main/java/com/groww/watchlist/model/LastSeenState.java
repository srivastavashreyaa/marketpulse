package com.groww.watchlist.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * The price a user last actually SAW for a symbol. This is what turns "what changed today"
 * into "what changed since YOU last looked". It is only updated when the user leaves the
 * page or presses "Mark all seen", never by background refreshes.
 */
@Entity
@Table(name = "last_seen_state",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "symbol"}),
        indexes = @Index(name = "idx_seen_user", columnList = "user_id"))
public class LastSeenState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 24)
    private String symbol;

    private double lastSeenPrice;
    private Instant lastSeenAt;

    protected LastSeenState() {}

    public LastSeenState(String userId, String symbol, double lastSeenPrice, Instant lastSeenAt) {
        this.userId = userId;
        this.symbol = symbol;
        this.lastSeenPrice = lastSeenPrice;
        this.lastSeenAt = lastSeenAt;
    }

    public String getSymbol() { return symbol; }
    public double getLastSeenPrice() { return lastSeenPrice; }
    public Instant getLastSeenAt() { return lastSeenAt; }

    public void update(double price, Instant at) {
        this.lastSeenPrice = price;
        this.lastSeenAt = at;
    }
}
