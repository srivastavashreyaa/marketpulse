package com.groww.watchlist.model;

import jakarta.persistence.*;

import java.time.Instant;

/** A registered user. Only a salted PBKDF2 hash of the password is stored. */
@Entity
@Table(name = "user_accounts", uniqueConstraints = @UniqueConstraint(columnNames = {"email"}))
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 190)
    private String email;

    @Column(nullable = false, length = 60)
    private String displayName;

    @Column(nullable = false, length = 200)
    private String passwordHash;

    @Column(nullable = false)
    private Instant createdAt;

    protected UserAccount() {}

    public UserAccount(String email, String displayName, String passwordHash, Instant createdAt) {
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
}
