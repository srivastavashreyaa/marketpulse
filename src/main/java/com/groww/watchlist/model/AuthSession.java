package com.groww.watchlist.model;

import jakarta.persistence.*;

import java.time.Instant;

/** A login session. The browser holds the random token; we store only its SHA-256 hash. */
@Entity
@Table(name = "auth_sessions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"token_hash"}),
        indexes = @Index(name = "idx_session_expiry", columnList = "expiresAt"))
public class AuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private Instant expiresAt;

    protected AuthSession() {}

    public AuthSession(String tokenHash, Long accountId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.accountId = accountId;
        this.expiresAt = expiresAt;
    }

    public Long getAccountId() { return accountId; }
    public Instant getExpiresAt() { return expiresAt; }
}
