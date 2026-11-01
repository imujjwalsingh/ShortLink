package com.shortlink.analyticsservice.event;

import java.time.Instant;

/**
 * Published to link-milestones (key = shortCode) when a link's click total
 * reaches one of the configured milestones. eventId is deterministic
 * (shortCode + milestone) so notification-service can drop duplicates and
 * never sends the same "100 clicks!" email twice.
 */
public record LinkMilestoneEvent(
        String eventId,
        String shortCode,
        String ownerId,
        long milestone,
        Instant occurredAt
) {
    public static LinkMilestoneEvent of(String shortCode, String ownerId, long milestone, Instant occurredAt) {
        return new LinkMilestoneEvent("milestone:" + shortCode + ":" + milestone, shortCode, ownerId, milestone, occurredAt);
    }
}
