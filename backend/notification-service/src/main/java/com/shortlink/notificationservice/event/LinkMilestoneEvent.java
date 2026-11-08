package com.shortlink.notificationservice.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/** Consumer-side copy of analytics-service's event (tolerant reader). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LinkMilestoneEvent(
        String eventId,
        String shortCode,
        String ownerId,
        long milestone,
        Instant occurredAt
) {
}
