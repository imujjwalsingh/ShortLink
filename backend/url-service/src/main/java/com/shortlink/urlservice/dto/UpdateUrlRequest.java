package com.shortlink.urlservice.dto;

import com.shortlink.urlservice.validation.HttpUrl;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Full replacement of a link's mutable fields (PUT semantics): omitting
 * expiresAt removes any existing expiry. The short code itself is
 * immutable, since changing it would break every copy already shared.
 */
public record UpdateUrlRequest(

        @NotBlank(message = "originalUrl is required")
        @Size(max = 2048, message = "originalUrl must be at most 2048 characters")
        @HttpUrl
        String originalUrl,

        @Future(message = "expiresAt must be in the future")
        Instant expiresAt
) {
}
