package com.shortlink.urlservice.exception;

public class AliasAlreadyExistsException extends RuntimeException {
    public AliasAlreadyExistsException(String alias) {
        super("Custom alias already in use: " + alias);
    }
}
