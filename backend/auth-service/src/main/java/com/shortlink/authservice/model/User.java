package com.shortlink.authservice.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A registered account. email is stored normalized (trimmed, lower-cased)
 * with a unique index, so "Bob@x.io" and "bob@x.io " are the same account.
 *
 * registrationEventPublished is a one-field transactional outbox: the user
 * and the "UserRegistered still needs publishing" marker are written in
 * the same single-document insert, which MongoDB guarantees is atomic
 * (no replica set or multi-document transaction needed). UserOutboxRelay
 * publishes pending events and flips the flag once Kafka has acknowledged
 * them, so a Kafka outage during sign-up delays the welcome email instead
 * of losing it.
 */
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String passwordHash;

    private Instant createdAt;

    @Indexed
    private boolean registrationEventPublished;

    public User() {
    }

    public User(String email, String passwordHash, Instant createdAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.registrationEventPublished = false;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isRegistrationEventPublished() {
        return registrationEventPublished;
    }
}
