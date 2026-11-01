package com.shortlink.urlservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * Minimal consecutive-failure circuit breaker for the Redis cache.
 *
 * Short timeouts alone make each failed Redis call cheap, but during an
 * outage every redirect would still pay that timeout. Once
 * failureThreshold calls in a row fail, the breaker opens and callers skip
 * Redis entirely (straight to Mongo) for openDuration. After that, exactly
 * one trial call is let through (half-open): success closes the breaker,
 * failure re-opens it for another openDuration.
 *
 * Hand-rolled rather than pulling in Resilience4j because this is the
 * only place the project needs one, and the whole state machine fits on
 * one screen, which also makes it easy to reason about in an interview.
 */
final class SimpleCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(SimpleCircuitBreaker.class);

    private final String name;
    private final int failureThreshold;
    private final long openDurationNanos;
    private final LongSupplier nanoClock;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicBoolean trialInFlight = new AtomicBoolean();
    private volatile boolean open;
    private volatile long openUntilNanos;

    SimpleCircuitBreaker(String name, int failureThreshold, Duration openDuration) {
        this(name, failureThreshold, openDuration, System::nanoTime);
    }

    SimpleCircuitBreaker(String name, int failureThreshold, Duration openDuration, LongSupplier nanoClock) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be >= 1");
        }
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.openDurationNanos = openDuration.toNanos();
        this.nanoClock = nanoClock;
    }

    /** True if the caller may attempt the protected call right now. */
    boolean allowRequest() {
        if (!open) {
            return true;
        }
        if (nanoClock.getAsLong() - openUntilNanos < 0) {
            return false;
        }
        // Open period elapsed: let a single trial call through.
        return trialInFlight.compareAndSet(false, true);
    }

    void onSuccess() {
        consecutiveFailures.set(0);
        trialInFlight.set(false);
        if (open) {
            open = false;
            log.info("Circuit '{}' closed: calls are succeeding again", name);
        }
    }

    void onFailure() {
        trialInFlight.set(false);
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            boolean wasOpen = open;
            openUntilNanos = nanoClock.getAsLong() + openDurationNanos;
            open = true;
            if (!wasOpen) {
                log.warn("Circuit '{}' opened after {} consecutive failures; skipping calls for {} ms",
                        name, failureThreshold, Duration.ofNanos(openDurationNanos).toMillis());
            }
        }
    }

    boolean isOpen() {
        return open;
    }
}
