package com.shortlink.urlservice.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Turns (client IP, user agent) into an opaque visitor id for
 * unique-visitor counts, so raw IPs never reach Kafka or Elasticsearch.
 *
 * It is an HMAC, not a plain hash, because the IPv4 space is small enough
 * to brute-force: anyone holding SHA-256(ip|ua) and the user agent could
 * recover the IP. Keyed with a secret, it can't be reversed without the
 * key. Without a configured key, a random one is generated per process,
 * which still works but makes visitor ids change on restart and differ
 * between instances.
 */
@Component
public class VisitorHasher {

    private static final Logger log = LoggerFactory.getLogger(VisitorHasher.class);
    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public VisitorHasher(@Value("${app.analytics.visitor-hash-key:}") String configuredKey) {
        byte[] keyBytes;
        if (configuredKey == null || configuredKey.isBlank()) {
            keyBytes = new byte[32];
            new SecureRandom().nextBytes(keyBytes);
            log.info("app.analytics.visitor-hash-key not set; using a random per-process key "
                    + "(unique-visitor ids will change on restart)");
        } else {
            keyBytes = configuredKey.getBytes(StandardCharsets.UTF_8);
        }
        this.key = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    public String hash(String clientIp, String userAgent) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal((nullToEmpty(clientIp) + "|" + nullToEmpty(userAgent))
                    .getBytes(StandardCharsets.UTF_8));
            // 128 bits is plenty to keep collisions out of visitor counts.
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
