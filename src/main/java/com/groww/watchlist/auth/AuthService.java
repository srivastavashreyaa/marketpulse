package com.groww.watchlist.auth;

import com.groww.watchlist.config.BadRequestException;
import com.groww.watchlist.model.AuthSession;
import com.groww.watchlist.model.UserAccount;
import com.groww.watchlist.repository.AuthSessionRepository;
import com.groww.watchlist.repository.UserAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Sign-up, login and cookie-session management. */
@Service
public class AuthService {

    public static final Duration SESSION_TTL = Duration.ofDays(30);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,120}\\.[^@\\s.]{2,}$");
    private static final Set<String> WEAK = Set.of("password", "password1", "12345678", "123456789", "1234567890",
            "qwertyui", "qwerty123", "iloveyou", "11111111", "abcd1234", "password123");

    private final UserAccountRepository accounts;
    private final AuthSessionRepository sessions;
    private final PasswordHasher hasher;
    private final LoginThrottle throttle;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserAccountRepository accounts, AuthSessionRepository sessions, PasswordHasher hasher, LoginThrottle throttle) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.hasher = hasher;
        this.throttle = throttle;
    }

    public UserAccount signup(String rawEmail, String password, String displayName) {
        String email = normalizeEmail(rawEmail);
        validatePassword(password);
        if (accounts.findByEmail(email).isPresent()) throw duplicate();
        try {
            return accounts.save(new UserAccount(email, cleanName(displayName, email), hasher.hash(password), Instant.now()));
        } catch (DataIntegrityViolationException raced) {
            throw duplicate();
        }
    }

    public UserAccount login(String rawEmail, String password) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        throttle.check(email);
        Optional<UserAccount> account = email.isEmpty() || password == null ? Optional.empty() : accounts.findByEmail(email);
        boolean ok;
        if (account.isPresent()) {
            ok = hasher.verify(password, account.get().getPasswordHash());
        } else {
            hasher.burn(password);
            ok = false;
        }
        if (!ok) {
            throttle.fail(email);
            throw new BadRequestException("Incorrect email or password.");
        }
        throttle.success(email);
        return account.get();
    }

    /** Returns the raw token to put in the cookie; only its hash is persisted. */
    @Transactional
    public String createSession(UserAccount account) {
        sessions.deleteByExpiresAtBefore(Instant.now());
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        sessions.save(new AuthSession(sha256(token), account.getId(), Instant.now().plus(SESSION_TTL)));
        return token;
    }

    public Optional<UserAccount> accountFor(String token) {
        if (token == null || token.length() < 20 || token.length() > 100) return Optional.empty();
        return sessions.findByTokenHash(sha256(token))
                .filter(s -> s.getExpiresAt().isAfter(Instant.now()))
                .flatMap(s -> accounts.findById(s.getAccountId()));
    }

    @Transactional
    public void logout(String token) {
        if (token != null && token.length() <= 100) sessions.deleteByTokenHash(sha256(token));
    }

    // ---- validation helpers -------------------------------------------------

    static String normalizeEmail(String raw) {
        String e = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (e.length() > 190 || !EMAIL.matcher(e).matches()) throw new BadRequestException("Enter a valid email address.");
        return e;
    }

    static void validatePassword(String p) {
        if (p == null || p.isEmpty()) throw new BadRequestException("Enter a password.");
        if (p.length() < 8) throw new BadRequestException("Use at least 8 characters for your password.");
        if (p.length() > 128) throw new BadRequestException("That password is too long (128 characters max).");
        if (WEAK.contains(p.toLowerCase(Locale.ROOT)) || p.chars().distinct().count() < 3) {
            throw new BadRequestException("That password is too easy to guess. Try something less common.");
        }
    }

    static String cleanName(String raw, String email) {
        String n = raw == null ? "" : raw.replaceAll("[\\p{Cntrl}<>]", "").trim().replaceAll("\\s+", " ");
        if (n.length() > 40) n = n.substring(0, 40).trim();
        return n.isEmpty() ? email.substring(0, email.indexOf('@')) : n;
    }

    private static BadRequestException duplicate() {
        return new BadRequestException("An account with this email already exists. Try logging in.");
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
