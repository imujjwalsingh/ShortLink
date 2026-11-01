package com.shortlink.authservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Size(max = 254, message = "email must be at most 254 characters")
        String email,

        // bcrypt only looks at the first 72 bytes, so longer passwords would
        // silently be truncated; reject them instead.
        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be 8-72 characters")
        String password
) {
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=***]";
    }
}
