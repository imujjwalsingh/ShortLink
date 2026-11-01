package com.shortlink.urlservice.dto;

import com.shortlink.urlservice.model.UrlMapping;

import java.time.Instant;

public record UrlResponse(
        String shortCode,
        String shortUrl,
        String originalUrl,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt,
        boolean active
) {
    public static UrlResponse from(UrlMapping mapping, String baseUrl) {
        return new UrlResponse(
                mapping.getShortCode(),
                baseUrl + "/" + mapping.getShortCode(),
                mapping.getOriginalUrl(),
                mapping.getCreatedAt(),
                mapping.getUpdatedAt(),
                mapping.getExpiresAt(),
                mapping.isActive()
        );
    }
}
