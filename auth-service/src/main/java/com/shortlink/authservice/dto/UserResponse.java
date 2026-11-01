package com.shortlink.authservice.dto;

import com.shortlink.authservice.model.User;

import java.time.Instant;

public record UserResponse(String id, String email, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getCreatedAt());
    }
}
