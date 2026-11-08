package com.shortlink.notificationservice.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * This service's own copy of "which email belongs to which user id", built
 * from UserRegistered events (event-carried state transfer). Sending a
 * milestone email therefore needs no synchronous call to auth-service, so
 * auth-service being down doesn't stop notifications.
 */
@Document(collection = "contacts")
public record Contact(@Id String userId, String email, Instant updatedAt) {
}
