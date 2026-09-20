package com.shortlink.urlservice.exception;

public class LinkExpiredException extends RuntimeException {
    public LinkExpiredException(String shortCode) {
        super("Link has expired: " + shortCode);
    }
}
