package com.feurstagram.extension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Local friction gate for the optional recommendations beyond Home's caught-up card.
 * The expected password is stored as a SHA-256 digest rather than visible plaintext.
 * This is not an account-security mechanism: anyone who can modify the APK can bypass it.
 */
final class FeedUnlockCode {
    private FeedUnlockCode() {}

    private static final byte[] EXPECTED = hex(
            "f384c0e4f88dc9ea55c92ba016cb43b0d821e76c6676ada2d0de5e392976b30d");

    static boolean matches(String candidate) {
        if (candidate == null) return false;
        try {
            byte[] actual = MessageDigest.getInstance("SHA-256").digest(
                    candidate.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(EXPECTED, actual);
        } catch (NoSuchAlgorithmException ignored) {
            return false;
        }
    }

    private static byte[] hex(String string) {
        byte[] decoded = new byte[string.length() / 2];
        for (int i = 0; i < decoded.length; i++) {
            decoded[i] = (byte) ((Character.digit(string.charAt(i * 2), 16) << 4)
                    | Character.digit(string.charAt(i * 2 + 1), 16));
        }
        return decoded;
    }
}
