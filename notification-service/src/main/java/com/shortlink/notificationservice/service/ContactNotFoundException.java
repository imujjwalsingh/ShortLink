package com.shortlink.notificationservice.service;

/**
 * A milestone arrived for a user whose UserRegistered event hasn't been
 * processed yet. The two topics aren't ordered relative to each other, so
 * this is retryable: the Kafka error handler backs off and tries again,
 * by which time the contact usually exists.
 */
public class ContactNotFoundException extends RuntimeException {
    public ContactNotFoundException(String userId) {
        super("No contact for user " + userId + " yet");
    }
}
