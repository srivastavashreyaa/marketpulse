package com.groww.watchlist.controller;

import com.groww.watchlist.auth.AuthService;
import com.groww.watchlist.auth.Owners;
import com.groww.watchlist.config.BadRequestException;
import com.groww.watchlist.dto.AuthStatus;
import com.groww.watchlist.dto.LoginRequest;
import com.groww.watchlist.dto.SignupRequest;
import com.groww.watchlist.model.UserAccount;
import com.groww.watchlist.service.WatchlistService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Cookie-session auth. The session cookie is HttpOnly (invisible to JavaScript), SameSite=Lax (not sent on
 * cross-site POSTs, which is the CSRF defence), and Secure whenever the request arrived over HTTPS.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String COOKIE = "sw_session";

    private final AuthService auth;
    private final WatchlistService watchlist;

    public AuthController(AuthService auth, WatchlistService watchlist) {
        this.auth = auth;
        this.watchlist = watchlist;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthStatus> signup(@RequestBody SignupRequest req,
                                             @RequestHeader(value = "X-Forwarded-Proto", required = false) String proto) {
        if (req == null) throw new BadRequestException("Invalid request");
        UserAccount account = auth.signup(req.email(), req.password(), req.displayName());
        // carry over whatever the visitor built as a guest, so signing up never loses their list
        if (Owners.validGuest(req.guestId())) watchlist.migrateGuest(req.guestId(), Owners.ownerKey(account));
        return loggedIn(account, proto);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthStatus> login(@RequestBody LoginRequest req,
                                            @RequestHeader(value = "X-Forwarded-Proto", required = false) String proto) {
        if (req == null) throw new BadRequestException("Invalid request");
        return loggedIn(auth.login(req.email(), req.password()), proto);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(value = COOKIE, required = false) String token,
                                       @RequestHeader(value = "X-Forwarded-Proto", required = false) String proto) {
        auth.logout(token);
        return ResponseEntity.noContent().header("Set-Cookie", cookie("", proto, 0)).build();
    }

    @GetMapping("/me")
    public AuthStatus me(@CookieValue(value = COOKIE, required = false) String token) {
        return auth.accountFor(token).map(a -> new AuthStatus(true, a.getEmail(), a.getDisplayName())).orElse(AuthStatus.guest());
    }

    private ResponseEntity<AuthStatus> loggedIn(UserAccount account, String proto) {
        String token = auth.createSession(account);
        return ResponseEntity.ok()
                .header("Set-Cookie", cookie(token, proto, AuthService.SESSION_TTL.toSeconds()))
                .body(new AuthStatus(true, account.getEmail(), account.getDisplayName()));
    }

    static String cookie(String value, String proto, long maxAgeSeconds) {
        return COOKIE + "=" + value + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + maxAgeSeconds
                + ("https".equalsIgnoreCase(proto) ? "; Secure" : "");
    }
}
