package com.shortlink.urlservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Core URL mapping record. short_code has a unique index — this is what the
 * redirect path and cache lookups key off of.
 *
 * owner_id is nullable for now (Auth Service doesn't exist yet in the build
 * order). Once JWT auth is wired in, this becomes required and endpoints get
 * an ownership check.
 */
@Document(collection = "url_mappings")
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

    private Instant expiresAt;

    @Builder.Default
    private boolean active = true;

    // Deliberately NOT storing a click count here. Click counts live in
    // Elasticsearch (Analytics Service), populated async via Kafka. Keeping
    // it out of MongoDB avoids a second source of truth that would need its
    // own reconciliation story.

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
