package com.shortlink.authservice.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
