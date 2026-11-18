package com.groww.watchlist.auth;

import com.groww.watchlist.config.BadRequestException;
import com.groww.watchlist.model.UserAccount;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Decides whose watchlist a request is for: the logged-in account if the session cookie is valid,
 * otherwise the anonymous guest id the browser generated.
 */
@Component
public class Owners {

    private static final Pattern GUEST = Pattern.compile("^[A-Za-z0-9\\-]{8,64}$");
    private static final String ACCOUNT_PREFIX = "acct-";

    private final AuthService auth;

    public Owners(AuthService auth) {
        this.auth = auth;
    }

    public String resolve(String sessionToken, String guestId) {
        Optional<UserAccount> account = auth.accountFor(sessionToken);
        if (account.isPresent()) return ownerKey(account.get());
        if (!validGuest(guestId)) throw new BadRequestException("Invalid user id");
        return guestId;
    }

    public static String ownerKey(UserAccount account) {
        return ACCOUNT_PREFIX + account.getId();
    }

    /** Guests may never claim an id in the account namespace, or they could read someone else's list. */
    public static boolean validGuest(String id) {
        return id != null && GUEST.matcher(id).matches() && !id.startsWith(ACCOUNT_PREFIX);
    }
}
