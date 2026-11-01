package com.shortlink.analyticsservice.event;

/**
 * A record that can never be processed, however often it is retried
 * (malformed JSON, missing required fields). The Kafka error handler sends
 * these straight to the dead-letter topic without retrying.
 */
public class InvalidEventException extends RuntimeException {
    public InvalidEventException(String message) {
        super(message);
    }
}
