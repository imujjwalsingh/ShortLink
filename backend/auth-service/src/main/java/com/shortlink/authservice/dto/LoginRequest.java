package com.shortlink.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "email is required")
        @Size(max = 254)
        String email,

        @NotBlank(message = "password is required")
        @Size(max = 72)
        String password
) {
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
