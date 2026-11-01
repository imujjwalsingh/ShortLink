package com.shortlink.urlservice.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleCircuitBreakerTest {

    private final AtomicLong clock = new AtomicLong(-5_000_000_000L); // nanoTime can be negative
    private final SimpleCircuitBreaker breaker =
            new SimpleCircuitBreaker("test", 3, Duration.ofSeconds(10), clock::get);

    @Test
    void staysClosedBelowThreshold() {
        breaker.onFailure();
        breaker.onFailure();
        assertThat(breaker.allowRequest()).isTrue();
        assertThat(breaker.isOpen()).isFalse();
    }

    @Test
    void successResetsTheFailureCount() {
        breaker.onFailure();
        breaker.onFailure();
        breaker.onSuccess();
        breaker.onFailure();
        breaker.onFailure();
        assertThat(breaker.isOpen()).isFalse();
    }

    @Test
    void opensAtThresholdAndRejectsUntilCooldownElapses() {
        tripBreaker();

        assertThat(breaker.allowRequest()).isFalse();
        clock.addAndGet(Duration.ofSeconds(9).toNanos());
        assertThat(breaker.allowRequest()).isFalse();
    }

    @Test
    void allowsExactlyOneTrialAfterCooldownAndClosesOnSuccess() {
        tripBreaker();
        clock.addAndGet(Duration.ofSeconds(10).toNanos());

        assertThat(breaker.allowRequest()).isTrue();
        assertThat(breaker.allowRequest()).isFalse();

        breaker.onSuccess();
        assertThat(breaker.isOpen()).isFalse();
        assertThat(breaker.allowRequest()).isTrue();
    }

    @Test
    void failedTrialReopensForAnotherFullCooldown() {
        tripBreaker();
        clock.addAndGet(Duration.ofSeconds(10).toNanos());
        assertThat(breaker.allowRequest()).isTrue();

        breaker.onFailure();

        assertThat(breaker.allowRequest()).isFalse();
        clock.addAndGet(Duration.ofSeconds(10).toNanos());
        assertThat(breaker.allowRequest()).isTrue();
    }

    private void tripBreaker() {
        for (int i = 0; i < 3; i++) {
            breaker.onFailure();
        }
        assertThat(breaker.isOpen()).isTrue();
    }
}
