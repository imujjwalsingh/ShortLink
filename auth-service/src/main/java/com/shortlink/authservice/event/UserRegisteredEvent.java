package com.shortlink.authservice.event;

import java.time.Instant;

/**
 * Published to the user-registered topic (key = userId).
 *
 * eventId is derived from the user id rather than random, so if the outbox
 * relay publishes the same registration twice (it crashed after Kafka
 * acknowledged but before the flag was saved), consumers see the same id
 * and can drop the duplicate.
 */
public record UserRegisteredEvent(
        String eventId,
        String userId,
        String email,
        Instant occurredAt
) {
    public static UserRegisteredEvent of(String userId, String email, Instant occurredAt) {
        return new UserRegisteredEvent("user-registered:" + userId, userId, email, occurredAt);
    }
}
