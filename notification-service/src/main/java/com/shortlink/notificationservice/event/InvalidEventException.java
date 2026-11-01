package com.shortlink.notificationservice.event;

/** A record that can never be processed; dead-lettered without retries. */
public class InvalidEventException extends RuntimeException {
    public InvalidEventException(String message) {
        super(message);
    }
}
