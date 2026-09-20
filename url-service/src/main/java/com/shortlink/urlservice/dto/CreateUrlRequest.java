package com.shortlink.urlservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public record CreateUrlRequest(

        @NotBlank(message = "originalUrl is required")
        @Pattern(regexp = "^https?://.+", message = "originalUrl must start with http:// or https://")
        String originalUrl,

        @Pattern(regexp = "^[a-zA-Z0-9_-]{3,32}$", message = "customAlias must be 3-32 chars, alphanumeric/underscore/hyphen")
        String customAlias,

        Instant expiresAt
) {
}
