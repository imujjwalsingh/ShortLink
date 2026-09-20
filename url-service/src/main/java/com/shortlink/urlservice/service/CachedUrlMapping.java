package com.shortlink.urlservice.service;

import com.shortlink.urlservice.model.UrlMapping;

import java.time.Instant;

/**
 * What gets stored in Redis for the redirect hot path. Deliberately not the
 * full UrlMapping — no owner_id, no id — just enough to answer "where does
 * this redirect to, and is it still valid" without a second round trip to
 * Mongo to re-check active/expiresAt.
 */
public record CachedUrlMapping(
        String shortCode,
        String originalUrl,
        boolean active,
        Instant expiresAt
) {
    public static CachedUrlMapping from(UrlMapping mapping) {
        return new CachedUrlMapping(
                mapping.getShortCode(),
                mapping.getOriginalUrl(),
                mapping.isActive(),
                mapping.getExpiresAt()
        );
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
