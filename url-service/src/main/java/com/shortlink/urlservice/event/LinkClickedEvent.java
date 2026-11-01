package com.shortlink.urlservice.event;

import java.time.Instant;

/**
 * Published to the link-clicks topic (key = shortCode, so all clicks for a
 * link land on one partition, in order) for every successful redirect.
 *
 * eventId is a random UUID minted here, once per click. It is the
 * idempotency key downstream: analytics-service uses it as the
 * Elasticsearch document id, so a redelivered message overwrites nothing
 * and is never counted twice.
 *
 * The raw client IP never leaves url-service; only visitorHash, a keyed
 * hash of IP + user agent, does (enough for unique-visitor counts).
 */
public record LinkClickedEvent(
        String eventId,
        String shortCode,
        String ownerId,
        Instant occurredAt,
        String referrer,
        String userAgent,
        String visitorHash
) {
}
