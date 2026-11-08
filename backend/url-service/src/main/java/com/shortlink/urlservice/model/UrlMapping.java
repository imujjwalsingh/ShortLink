package com.shortlink.urlservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Core URL mapping record.
 *
 * Indexes (created at startup because spring.data.mongodb.auto-index-creation
 * is on, which Spring Data MongoDB 3+ leaves off by default):
 *  - unique on shortCode: the redirect path and cache key off it, and it
 *    is the real guarantee of code uniqueness. UrlService relies on the
 *    duplicate-key error from this index instead of a racy exists() check.
 *  - ownerId + createdAt: backs the "list my links, newest first" query.
 *
 * version is Spring Data's optimistic-locking field. Every save checks and
 * bumps it, so two concurrent edits can't silently overwrite each other
 * (the loser gets a 409). It also orders cache writes: UrlCacheService only
 * accepts an entry whose version is newer than what Redis already holds,
 * which is what stops a slow reader from writing a stale copy back into the
 * cache after a deactivate or edit.
 *
 * ownerId is the auth-service user id (JWT subject) of whoever created the
 * link. Links created before auth existed have a null owner and can no
 * longer be managed through the API.
 */
@Document(collection = "url_mappings")
@CompoundIndex(name = "owner_created_idx", def = "{'ownerId': 1, 'createdAt': -1}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UrlMapping {

    @Id
    private String id;

    @Indexed(unique = true)
    private String shortCode;

    private String originalUrl;

    private String ownerId;

    private Instant createdAt;

    private Instant updatedAt;

    private Instant expiresAt;

    @Builder.Default
    private boolean active = true;

    @Version
    private Long version;

    // Deliberately NOT storing a click count here. Click counts live in
    // Elasticsearch (analytics-service), populated async via Kafka. Keeping
    // it out of MongoDB avoids a second source of truth that would need its
    // own reconciliation story.

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isOwnedBy(String userId) {
        return ownerId != null && ownerId.equals(userId);
    }
}
