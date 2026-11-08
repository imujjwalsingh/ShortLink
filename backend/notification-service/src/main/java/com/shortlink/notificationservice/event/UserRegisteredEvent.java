package com.shortlink.notificationservice.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/** Consumer-side copy of auth-service's event (tolerant reader). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserRegisteredEvent(
        String eventId,
        String userId,
        String email,
        Instant occurredAt
) {
}
