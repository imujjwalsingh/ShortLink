package com.shortlink.urlservice.service;

/**
 * Encodes a positive long into a base62 string ([0-9a-zA-Z]).
 *
 * Why base62-over-a-counter instead of hashing the URL (e.g. MD5 + truncate):
 *  - Guaranteed no collisions (the counter is unique by construction), so no
 *    retry/collision-check loop is needed on the write path.
 *  - Short codes stay compact and predictable in length as the counter grows
 *    (7 base62 chars covers ~3.5 trillion IDs).
 *  - Trade-off: codes are sequential/guessable-ish (aB3xY, aB3xZ, ...), which
 *    leaks approximate creation order and volume. A hash-based approach
 *    avoids that but needs collision handling and doesn't shorten as
 *    predictably. For a portfolio project the counter approach is the
 *    cleaner story and the one worth defending in an interview.
 */
public final class Base62Encoder {

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    private Base62Encoder() {
    }

    public static String encode(long value) {
        if (value == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }
        StringBuilder sb = new StringBuilder();
        long n = value;
        while (n > 0) {
            int rem = (int) (n % BASE);
            sb.append(ALPHABET.charAt(rem));
            n /= BASE;
        }
        return sb.reverse().toString();
    }
}
