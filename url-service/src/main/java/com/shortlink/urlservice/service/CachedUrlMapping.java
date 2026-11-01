package com.shortlink.urlservice.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.shortlink.urlservice.model.UrlMapping;

import java.time.Instant;

/**
 * What gets stored in Redis for the redirect hot path. Deliberately not the
 * full UrlMapping (no Mongo id, no timestamps), just enough to answer
 * "where does this redirect to, and is it still valid" plus the owner id
 * that goes into the click event, without a second round trip to Mongo.
 *
 * version mirrors UrlMapping.version and is what UrlCacheService compares
 * to reject stale writes. A "negative" entry (code known not to exist) has
 * originalUrl == null and version NEGATIVE_VERSION, which is lower than
 * any real document's version, so creating the link always replaces it.
 */
public record CachedUrlMapping(
        String shortCode,
        String originalUrl,
        String ownerId,
        boolean active,
        Instant expiresAt,
        long version
) {
    public static final long NEGATIVE_VERSION = -1L;

    public static CachedUrlMapping from(UrlMapping mapping) {
        return new CachedUrlMapping(
                mapping.getShortCode(),
                mapping.getOriginalUrl(),
                mapping.getOwnerId(),
                mapping.isActive(),
                mapping.getExpiresAt(),
                mapping.getVersion() == null ? 0L : mapping.getVersion()
        );
    }

    public static CachedUrlMapping notFound(String shortCode) {
        return new CachedUrlMapping(shortCode, null, null, false, null, NEGATIVE_VERSION);
    }

    @JsonIgnore
    public boolean isNotFound() {
        return version == NEGATIVE_VERSION;
    }

    @JsonIgnore
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
