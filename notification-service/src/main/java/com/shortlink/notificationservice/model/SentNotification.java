package com.shortlink.notificationservice.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One row per email actually sent, keyed by the triggering event's id. It
 * is the dedupe record that turns Kafka's at-least-once delivery into "at
 * most one email per event" in the normal case.
 */
@Document(collection = "sent_notifications")
public record SentNotification(@Id String eventId, String type, String recipient, Instant sentAt) {
}
