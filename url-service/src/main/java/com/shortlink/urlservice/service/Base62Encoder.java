package com.shortlink.urlservice.service;

/**
 * Encodes a non-negative long into a base62 string ([0-9A-Za-z]).
 *
 * Why base62-over-a-counter instead of hashing the URL (e.g. MD5 + truncate):
 *  - The counter never repeats, so two generated codes can never collide.
 *    (A generated code CAN land on a custom alias someone already claimed;
 *    UrlService handles that by skipping to the next counter value.)
 *  - Short codes stay compact and predictable in length as the counter grows
 *    (7 base62 chars covers ~3.5 trillion IDs).
 *  - Trade-off: codes are sequential/guessable-ish (aB3xY, aB3xZ, ...), which
 *    leaks approximate creation order and volume, and lets anyone walk the
 *    redirect space. Management endpoints are owner-only, so walking codes
 *    only reveals destinations that are public by nature of being short
 *    links. A keyed permutation of the counter (e.g. a small Feistel network)
 *    would hide the order without giving up collision-freedom.
 */
public final class Base62Encoder {

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    private Base62Encoder() {
    }

    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Cannot base62-encode a negative value: " + value);
        }
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
