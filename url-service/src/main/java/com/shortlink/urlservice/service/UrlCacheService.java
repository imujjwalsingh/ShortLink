package com.shortlink.urlservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Cache-aside layer in front of the redirect lookup.
 *
 * <h3>Fails open, and fails fast</h3>
 * Any Redis error (connection refused, timeout, serialization issue) is
 * caught, logged, and treated as a cache miss, never propagated. Redis is
 * an optimization here, not a source of truth. Failing open is only half
 * the job, though: a Redis that hangs would still add its timeout to every
 * redirect. So commands have a 250 ms timeout, Lettuce rejects commands
 * immediately while disconnected instead of queueing them (RedisConfig),
 * and after a few consecutive failures a circuit breaker skips Redis
 * entirely for a cool-down period.
 *
 * <h3>Version-checked writes instead of evict()</h3>
 * Plain cache-aside has a classic race: a reader loads the link from Mongo,
 * a writer deactivates it and evicts the cache, then the reader writes its
 * now-stale copy back, and the deactivated link keeps redirecting for a
 * full TTL. Here every write, whether a cache fill or a write-through after
 * a mutation, goes through a Lua script that stores the entry only if its
 * version is strictly newer than the one Redis already holds. Mongo's
 * optimistic-locking version increases on every change, so a stale reader
 * can never overwrite a newer entry, whatever order things happen in.
 *
 * Entries are hashes {v: version, d: json} so the script can compare
 * versions without decoding JSON. The key prefix is versioned (v2) so
 * leftover plain-string entries from the old cache format are never read.
 *
 * <h3>Negative caching</h3>
 * Lookups for codes that don't exist are cached for a short TTL as a
 * NEGATIVE_VERSION (-1) entry, so junk traffic doesn't hit Mongo for every
 * request. Creating the link writes version 0 through, which replaces the
 * negative entry immediately.
 *
 * TTLs remain as a self-healing backstop for anything the write-through
 * misses (e.g. Redis was briefly unreachable during a mutation).
 */
@Component
public class UrlCacheService {

    private static final Logger log = LoggerFactory.getLogger(UrlCacheService.class);

    static final String KEY_PREFIX = "shortlink:url:v2:";
    private static final String FIELD_DATA = "d";

    static final RedisScript<Long> PUT_IF_NEWER = new DefaultRedisScript<>("""
            local current = redis.call('HGET', KEYS[1], 'v')
            if current and tonumber(current) >= tonumber(ARGV[1]) then
              return 0
            end
            redis.call('HSET', KEYS[1], 'v', ARGV[1], 'd', ARGV[2])
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final Duration negativeTtl;
    private final SimpleCircuitBreaker circuitBreaker;

    @Autowired // two constructors: tell Spring which one to use
    public UrlCacheService(StringRedisTemplate redisTemplate,
                           ObjectMapper objectMapper,
                           @Value("${app.cache.ttl-seconds:3600}") long ttlSeconds,
                           @Value("${app.cache.negative-ttl-seconds:60}") long negativeTtlSeconds,
                           @Value("${app.cache.circuit-breaker.failure-threshold:5}") int failureThreshold,
                           @Value("${app.cache.circuit-breaker.open-duration:30s}") Duration openDuration) {
        this(redisTemplate, objectMapper, Duration.ofSeconds(ttlSeconds), Duration.ofSeconds(negativeTtlSeconds),
                new SimpleCircuitBreaker("redis-cache", failureThreshold, openDuration));
    }

    UrlCacheService(StringRedisTemplate redisTemplate,
                    ObjectMapper objectMapper,
                    Duration ttl,
                    Duration negativeTtl,
                    SimpleCircuitBreaker circuitBreaker) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
        this.negativeTtl = negativeTtl;
        this.circuitBreaker = circuitBreaker;
    }

    public Optional<CachedUrlMapping> get(String shortCode) {
        if (!circuitBreaker.allowRequest()) {
            return Optional.empty();
        }
        try {
            Object json = redisTemplate.opsForHash().get(KEY_PREFIX + shortCode, FIELD_DATA);
            circuitBreaker.onSuccess();
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json.toString(), CachedUrlMapping.class));
        } catch (Exception e) {
            recordFailure("GET", shortCode, e);
            return Optional.empty();
        }
    }

    /** Caches a mapping unless Redis already holds the same or a newer version. */
    public void put(CachedUrlMapping mapping) {
        write(mapping, ttl);
    }

    /** Remembers, briefly, that a short code does not exist. */
    public void putNotFound(String shortCode) {
        write(CachedUrlMapping.notFound(shortCode), negativeTtl);
    }

    private void write(CachedUrlMapping mapping, Duration entryTtl) {
        if (!circuitBreaker.allowRequest()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(mapping);
            redisTemplate.execute(PUT_IF_NEWER,
                    List.of(KEY_PREFIX + mapping.shortCode()),
                    Long.toString(mapping.version()),
                    json,
                    Long.toString(Math.max(1, entryTtl.toSeconds())));
            circuitBreaker.onSuccess();
        } catch (Exception e) {
            recordFailure("PUT", mapping.shortCode(), e);
        }
    }

    private void recordFailure(String op, String shortCode, Exception e) {
        circuitBreaker.onFailure();
        // Log at WARN until the breaker opens; after that the breaker's own
        // log line says Redis is being skipped, so per-call WARNs are noise.
        if (circuitBreaker.isOpen()) {
            log.debug("Redis {} failed for shortCode={}: {}", op, shortCode, e.toString());
        } else {
            log.warn("Redis {} failed for shortCode={}, falling back to Mongo: {}", op, shortCode, e.toString());
        }
    }
}
