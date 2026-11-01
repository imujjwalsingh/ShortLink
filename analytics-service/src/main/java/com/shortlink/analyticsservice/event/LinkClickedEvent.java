package com.shortlink.analyticsservice.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Consumer-side copy of url-service's click event. Deliberately not a
 * shared library class: each service owns its view of the contract, and
 * unknown fields are ignored (tolerant reader), so url-service can add
 * fields without a lock-step release here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
