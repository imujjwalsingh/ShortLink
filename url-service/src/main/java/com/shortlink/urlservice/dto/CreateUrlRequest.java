package com.shortlink.urlservice.dto;

import com.shortlink.urlservice.validation.HttpUrl;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateUrlRequest(

        @NotBlank(message = "originalUrl is required")
        @Size(max = 2048, message = "originalUrl must be at most 2048 characters")
        @HttpUrl
        String originalUrl,

        @Pattern(regexp = "^[a-zA-Z0-9_-]{3,32}$", message = "customAlias must be 3-32 chars, alphanumeric/underscore/hyphen")
        String customAlias,

        @Future(message = "expiresAt must be in the future")
        Instant expiresAt
) {
}
