package com.groww.watchlist.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password hashing with PBKDF2-HMAC-SHA256 (JDK built-in, OWASP-recommended 210,000 iterations),
 * a random 16-byte salt per password, and constant-time comparison.
 * Stored format: {@code pbkdf2_sha256$iterations$salt$hash}.
 */
@Component
public class PasswordHasher {

    private static final String PREFIX = "pbkdf2_sha256";
    private static final int SALT_BYTES = 16, KEY_BITS = 256;

    private final int iterations;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public PasswordHasher(@Value("${app.auth.pbkdf2-iterations:210000}") int iterations) {
        this.iterations = iterations;
        this.dummyHash = hash("not-a-real-password");
    }

    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] h = derive(password.toCharArray(), salt, iterations);
        Base64.Encoder b64 = Base64.getEncoder().withoutPadding();
        return PREFIX + "$" + iterations + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(h);
    }

    public boolean verify(String password, String stored) {
        try {
            String[] p = stored.split("\\$");
            if (p.length != 4 || !PREFIX.equals(p[0])) return false;
            int it = Integer.parseInt(p[1]);
            if (it < 1 || it > 5_000_000) return false;
            byte[] salt = Base64.getDecoder().decode(p[2]);
            byte[] expected = Base64.getDecoder().decode(p[3]);
            byte[] actual = derive(password.toCharArray(), salt, it);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Spend the same time as a real check, so "unknown email" and "wrong password" are indistinguishable by timing. */
    public void burn(String password) {
        verify(password == null ? "" : password, dummyHash);
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return f.generateSecret(new PBEKeySpec(password, salt, iterations, KEY_BITS)).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        }
    }
}
