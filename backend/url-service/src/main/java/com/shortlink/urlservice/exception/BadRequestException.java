package com.shortlink.urlservice.exception;

/** A request that passed bean validation but breaks a business rule. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
